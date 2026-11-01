# 주인: AI-B
"""매칭 평가 실행: 평가 수료생 프로필 · 평가 색인 · 추천 결과 파일. 시연 서버 · 시연 색인(jobs_index.json)과 분리한다.

실행 (cd ai):
    python -m matching.eval_run profiles [--dry-run]          # ① 20명 프로필 → ai/data/eval/profiles/student_<id>.json (있으면 건너뜀 · LLM)
    python -m matching.eval_run index [--dry-run]             # ② 색인 → ai/data/index/eval_pool379.json · eval_gold91.json (임베딩)
    python -m matching.eval_run run --split dev --index gold91  # ③ → ai/eval/runs/matching/<날짜>/student_<id>.json · _run_<split>.json
    python -m matching.eval_run run --split dev --index gold91 --cache --candidate-k 50   # 첫 실행: 임베딩 · 리랭커 점수를 캐시에 저장
    python -m matching.eval_run run --split dev --index gold91 --cache-only               # 그다음: 캐시로만 (Gemini · 리랭커 호출 없음)

- 프로필은 /ai/profile/extract 와 같은 순서(규칙 → LLM → 근거 검사)로 한 번만 뽑아 파일로 고정한다 (LLM 결과가 실행마다 달라지지 않게).
- gold91 색인은 pool379 색인에서 정답 · 함정 공고만 복사한다 (임베딩 다시 안 함).
- test 수료생은 파일만 저장하고 화면에는 건수 · 형식 오류 · 시간만 출력한다.
- items 마다 공고의 최소 학력 · 근무지 시 · 도 · 경력 칸을 같이 저장한다 (코드 필터와 루브릭 기준 차이 분석용).
- 캐시(--cache): 검색어 임베딩과 리랭커 점수(검색어 · 공고 본문 쌍)를 ai/eval/runs/matching/_cache/ 에 저장하고 다시 쓴다 (커밋 안 됨).
  후보 수 K 를 크게(--candidate-k 50) 한 번 받아 두면 그보다 작은 K 실험도 캐시로 된다.
  --cache-only 는 캐시에 없는 값이 필요하면 호출하지 않고 멈춘다.
"""

import argparse
import ast
import hashlib
import json
import logging
import math
import re
import statistics
import subprocess
import sys
import time
from dataclasses import asdict, is_dataclass
from datetime import date
from pathlib import Path
from typing import Any

from pydantic import ValidationError

from matching import config
from matching.indexer import default_model_name, run_index
from matching.models import IndexRequest, RecommendRequest
from matching.preprocess import unify_region
from matching.recommend import _DEFAULT, recommend_jobs
from matching.store import DEFAULT_PATH, IndexStore

AI_ROOT = Path(__file__).resolve().parent.parent
SPLIT_FILE = AI_ROOT / "eval" / "split.json"
MATERIALS_FILE = AI_ROOT / "eval" / "personas" / "out" / "student_materials.json"
POSTING_FILES = {
    "real": AI_ROOT / "data" / "eval" / "ieum_jobs_20261028.json",
    "general": AI_ROOT / "data" / "eval" / "jobs_general.json",
    "gold": AI_ROOT / "eval" / "postings" / "out" / "eval_postings.json",
}
PROFILES_DIR = AI_ROOT / "data" / "eval" / "profiles"
INDEX_FILES = {
    "pool379": AI_ROOT / "data" / "index" / "eval_pool379.json",
    "gold91": AI_ROOT / "data" / "index" / "eval_gold91.json",
}
RUNS_DIR = AI_ROOT / "eval" / "runs" / "matching"
CACHE_FILE = RUNS_DIR / "_cache" / "scores.json"

EMBED_BATCH = 100  # common.embedding.BATCH_SIZE 와 같게 (예상 호출 수 계산용)

matching_logger = logging.getLogger("ieum.matching")


# ---------- 공용 ----------

def load_split() -> dict[str, list[int]]:
    data = json.loads(SPLIT_FILE.read_text(encoding="utf-8"))
    return {"dev": data["dev"], "test": data["test"]}


def split_of(student_id: int, split: dict[str, list[int]]) -> str:
    return "dev" if student_id in split["dev"] else "test" if student_id in split["test"] else "unknown"


def load_postings() -> dict[int, dict]:
    """평가 공고 379건 { jobPostingId: 공고 } (출처 키 sourceFile 추가)"""
    postings = {}
    for name, path in POSTING_FILES.items():
        data = json.loads(path.read_text(encoding="utf-8"))
        for job in data["jobs"] if isinstance(data, dict) else data:  # 실제 공고 파일은 { jobs: [...] }
            postings[int(job["jobPostingId"])] = {**job, "sourceFile": name}
    return postings


def ensure_not_demo(path: Path) -> Path:
    """시연 색인(jobs_index.json)을 평가가 덮어쓰거나 읽지 않게 막는다."""
    if Path(path).resolve() == DEFAULT_PATH.resolve():
        raise SystemExit(f"시연 색인 경로는 평가에 쓸 수 없어요: {path}")
    return Path(path)


def write_json(path: Path, data: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def git_commit() -> str | None:
    try:
        head = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=AI_ROOT, capture_output=True, text=True,
                              check=True).stdout.strip()
        dirty = subprocess.run(["git", "status", "--porcelain", "--", "."], cwd=AI_ROOT, capture_output=True,
                               text=True).stdout.strip()
        return head + ("-dirty" if dirty else "")
    except (OSError, subprocess.CalledProcessError):
        return None


# ---------- ① 프로필 ----------

def extract_profile(material: dict) -> dict:
    """/ai/profile/extract 와 같은 처리. 반환: 파일에 저장할 dict (프로필 + LLM 상태 · 지표)"""
    from profile.evidence_check import allowed_refs, check_evidence
    from profile.llm_extract import extract_by_llm
    from profile.models import ExtractRequest
    from profile.rules import add_skills, extract_by_rules

    req = ExtractRequest.model_validate(material)
    profile = extract_by_rules(req)
    llm = extract_by_llm(req)
    profile = profile.model_copy(
        update={"roles": llm.roles, "achievements": llm.achievements, "skills": add_skills(profile.skills, llm.skills)}
    )
    profile = check_evidence(profile, allowed_refs(req))
    metrics = asdict(llm.metrics) if is_dataclass(llm.metrics) else None
    return {
        "studentId": req.student_id,
        "llmStatus": llm.status,
        "metrics": metrics,
        "excludedReasons": sorted(f"{e.kind}:{e.reason}" for e in llm.excluded),  # text 는 남기지 않는다
        "profile": profile.model_dump(by_alias=True),
    }


def profiles_plan(materials: list[dict], profiles_dir: Path) -> tuple[list[dict], int]:
    """아직 파일이 없는 수료생과 그중 LLM 을 부를 수료생 수"""
    from profile.llm_extract import has_llm_material
    from profile.models import ExtractRequest

    todo = [m for m in materials if not (profiles_dir / f"student_{m['studentId']}.json").exists()]
    llm_calls = 0
    for m in todo:
        try:
            llm_calls += has_llm_material(ExtractRequest.model_validate(m))
        except ValidationError:
            pass
    return todo, llm_calls


def cmd_profiles(dry_run: bool, profiles_dir: Path = PROFILES_DIR) -> None:
    split = load_split()
    materials = json.loads(MATERIALS_FILE.read_text(encoding="utf-8"))
    todo, llm_calls = profiles_plan(materials, profiles_dir)
    print(f"프로필: 전체 {len(materials)}명 · 새로 뽑을 {len(todo)}명 · 예상 LLM 호출 {llm_calls}번 (실패 시 1번씩 더)")
    if dry_run:
        return
    for m in todo:
        sid = int(m["studentId"])
        part = split_of(sid, split)
        started = time.perf_counter()
        try:
            data = extract_profile(m)
        except ValidationError as e:
            # 형식 오류는 test 수료생도 알린다 (어느 칸인지만, 값은 출력하지 않음)
            fields = sorted({".".join(str(p) for p in err["loc"]) for err in e.errors()})
            print(f"  student {sid} ({part}): 형식 오류 {len(fields)}칸 {fields}")
            continue
        data = {**data, "split": part, "extractMs": int((time.perf_counter() - started) * 1000)}
        write_json(profiles_dir / f"student_{sid}.json", data)
        print(f"  student {sid} ({part}): 저장 · llm={data['llmStatus']} · {data['extractMs']}ms")


# ---------- ② 색인 ----------

def index_jobs(postings: dict[int, dict]) -> list[dict]:
    """색인 요청 형식. contentHash 는 BE 와 같이 SHA-256(description) 으로 다시 계산한다 (jobs_general.json 값은 본문과 다름)"""
    keys = ("companyName", "title", "jobCategory", "location", "career", "education", "description")
    return [
        {"jobPostingId": jid, "contentHash": hashlib.sha256((job["description"] or "").encode("utf-8")).hexdigest(),
         **{k: job.get(k) for k in keys}}
        for jid, job in sorted(postings.items())
    ]


def cmd_index(dry_run: bool, *, embed: Any = _DEFAULT, model_name: Any = _DEFAULT,
              pool_path: Path = INDEX_FILES["pool379"], gold_path: Path = INDEX_FILES["gold91"]) -> None:
    pool_store = IndexStore(ensure_not_demo(pool_path))
    gold_store = IndexStore(ensure_not_demo(gold_path))
    postings = load_postings()
    jobs = index_jobs(postings)
    if model_name is _DEFAULT:
        model_name = default_model_name()

    old = pool_store.load()
    need = [j for j in jobs if not (
        (e := old.get(str(j["jobPostingId"]))) and e.get("vector") and e.get("contentHash") == j["contentHash"]
        and e.get("embeddingModel") == model_name
    )]
    print(f"색인: 공고 {len(jobs)}건 · 임베딩 필요 {len(need)}건 · 예상 임베딩 호출 {math.ceil(len(need) / EMBED_BATCH)}번"
          f" (gold91 은 pool379 에서 복사, 호출 없음)")
    if dry_run:
        return

    started = time.perf_counter()
    # embed 를 넘기지 않으면 indexer 기본 임베딩(common.embedding)을 쓴다 (_DEFAULT 는 recommend 의 것이라 그대로 넘기면 안 됨)
    kwargs = {} if embed is _DEFAULT else {"embed": embed}
    result = run_index(IndexRequest(jobs=jobs), pool_store, model_name=model_name, **kwargs)
    print(f"  pool379: indexed={result.indexed_count} skipped={result.skipped_count} failed={result.failed_count}"
          f" status={result.embedding_status} · {int((time.perf_counter() - started) * 1000)}ms")

    entries = pool_store.load()
    gold_ids = [jid for jid, job in postings.items() if job["sourceFile"] == "gold"]
    gold = {str(jid): entries[str(jid)] for jid in gold_ids if str(jid) in entries}
    gold_store.save(gold)
    no_vector = sum(1 for e in gold.values() if not e.get("vector"))
    print(f"  gold91: {len(gold)}건 복사 (벡터 없음 {no_vector}건)")


# ---------- ③ 추천 실행 ----------

class _LogCapture(logging.Handler):
    def __init__(self) -> None:
        super().__init__(logging.INFO)
        self.lines: list[str] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.lines.append(record.getMessage())


_DONE = re.compile(r"embed_ms=(\d+) rerank_ms=(\d+) total_ms=(\d+) top=(.*)$")
_EMPTY = re.compile(r"reason=(\w+)")


def parse_logs(lines: list[str]) -> dict:
    """recommend.py 로그 → 경로(OK · FALLBACK · EMPTY) · 빈 결과 이유 · 단계별 시간 · 리랭커 점수"""
    out: dict[str, Any] = {"path": "OK", "emptyReason": None, "fallbackReason": None,
                           "embedMs": None, "rerankMs": None, "scores": {}}
    for line in lines:
        if line.startswith("recommend fallback"):
            out["path"] = "FALLBACK"
            out["fallbackReason"] = line.split("reason=", 1)[-1]
        elif line.startswith("recommend empty") and out["path"] != "FALLBACK":
            out["path"] = "EMPTY"
            m = _EMPTY.search(line)
            out["emptyReason"] = m.group(1) if m else None
        elif line.startswith("recommend done") and (m := _DONE.search(line)):
            out["embedMs"], out["rerankMs"] = int(m.group(1)), int(m.group(2))
            try:
                out["scores"] = {str(jid): score for jid, score in ast.literal_eval(m.group(4))}
            except (ValueError, SyntaxError):
                pass
    return out


def card(item: dict, job: dict | None, scores: dict) -> dict:
    location = (job or {}).get("location")
    region = unify_region(location)
    return {
        "jobPostingId": item["jobPostingId"],
        "rank": item["rank"],
        "rerankScore": scores.get(str(item["jobPostingId"])),
        "title": (job or {}).get("title"),
        "companyName": (job or {}).get("companyName"),
        "sourceFile": (job or {}).get("sourceFile"),
        "career": (job or {}).get("career"),
        "education": (job or {}).get("education"),
        "location": location,
        "sido": region.split()[0] if region else None,
        "reasons": item["reasons"],
    }


def run_student(sid: int, profile_file: dict, material: dict | None, store: IndexStore, postings: dict[int, dict], *,
                embed_query: Any, rerank_fn: Any, model_name: Any) -> dict:
    profile = profile_file["profile"]
    req = RecommendRequest(student_id=sid, profile=profile, top_k=3)
    # 추천 로그(공고 id · 점수 포함)는 파일에만 담고 화면으로 흘려보내지 않는다 (test 수료생 결과 비공개)
    capture = _LogCapture()
    old_level, old_propagate = matching_logger.level, matching_logger.propagate
    matching_logger.addHandler(capture)
    matching_logger.setLevel(logging.INFO)
    matching_logger.propagate = False
    started = time.perf_counter()
    try:
        data = recommend_jobs(req, store, embed_query=embed_query, rerank_fn=rerank_fn, model_name=model_name)
    finally:
        total_ms = int((time.perf_counter() - started) * 1000)
        matching_logger.removeHandler(capture)
        matching_logger.setLevel(old_level)
        matching_logger.propagate = old_propagate
    logs = parse_logs(capture.lines)
    items = data.model_dump(by_alias=True)["items"]
    return {
        "studentId": sid,
        "student": {
            "desiredJob": profile.get("desiredJob"),
            "preferredRegion": profile.get("preferredRegion"),
            "education": [{"degree": e.get("degree"), "status": e.get("status")} for e in (material or {}).get("education", [])],
            "careers": len((material or {}).get("careers", [])),
        },
        "timing": {"totalMs": total_ms, "embedMs": logs["embedMs"], "rerankMs": logs["rerankMs"]},
        "path": logs["path"],
        "emptyReason": logs["emptyReason"],
        "fallbackReason": logs["fallbackReason"],
        "items": [card(it, postings.get(it["jobPostingId"]), logs["scores"]) for it in items],
    }


def _text_key(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


class ScoreCache:
    """검색어 임베딩 · 리랭커 점수 캐시. 키는 글의 SHA-256 이라 프로필 · 공고 본문이 바뀌면 다시 계산한다.

    { embedding: { 임베딩 모델: { 검색어 해시: vector } }, rerank: { 리랭커 모델: { 검색어 해시: { 공고 본문 해시: score } } } }
    only=True 면 캐시에 없을 때 호출하지 않고 missed 를 남긴다 (cmd_run 이 멈춘다).
    """

    def __init__(self, path: Path, *, embedding_model: str, reranker_model: str, only: bool = False):
        self.path = Path(path)
        self.data = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else {}
        self.vectors = self.data.setdefault("embedding", {}).setdefault(embedding_model, {})
        self.scores = self.data.setdefault("rerank", {}).setdefault(reranker_model, {})
        self.only = only
        self.missed = False
        self.calls = {"embedding": 0, "rerankPairs": 0}

    def _miss(self) -> None:
        self.missed = True
        raise RuntimeError("cache miss (--cache-only)")

    def embedder(self, real: Any) -> Any:
        def embed(texts):
            vectors = []
            for text in texts:
                key = _text_key(text)
                if key not in self.vectors:
                    if self.only:
                        self._miss()
                    self.vectors[key] = [float(v) for v in real([text])[0]]
                    self.calls["embedding"] += 1
                    self.save()
                vectors.append(self.vectors[key])
            return vectors

        return embed

    def reranker(self, real: Any) -> Any:
        def rerank(query, candidates, *, top_k=3):
            known = self.scores.setdefault(_text_key(query), {})
            keys = [_text_key(c) for c in candidates]
            missing = [i for i, k in enumerate(keys) if k not in known]
            if missing:
                if self.only:
                    self._miss()
                for index, score in real(query, [candidates[i] for i in missing], top_k=len(missing)):
                    known[keys[missing[index]]] = float(score)
                self.calls["rerankPairs"] += len(missing)
                self.save()
            ranked = sorted(((i, known[k]) for i, k in enumerate(keys)), key=lambda x: (-x[1], x[0]))
            return ranked[:top_k]

        return rerank

    def save(self) -> None:
        write_json(self.path, self.data)


def default_reranker_model() -> str:
    from common.config import get_settings
    from common.reranker import DEFAULT_MODEL

    return get_settings().reranker_model or DEFAULT_MODEL


def percentile(values: list[int], q: float) -> int | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, math.ceil(q * len(ordered)) - 1)]


def cmd_run(split_name: str, index_name: str, *, run_date: str | None = None, students: list[int] | None = None,
            index_path: Path | None = None, profiles_dir: Path = PROFILES_DIR, runs_dir: Path = RUNS_DIR,
            postings: dict[int, dict] | None = None, materials: dict[int, dict] | None = None,
            embed_query: Any = _DEFAULT, rerank_fn: Any = _DEFAULT, model_name: Any = _DEFAULT,
            warmup: Any = _DEFAULT, cache: ScoreCache | None = None, candidate_k: int | None = None) -> Path:
    old_k = config.CANDIDATE_K
    if candidate_k is not None:
        config.CANDIDATE_K = candidate_k  # 이번 실행에만. 끝나면 되돌린다
    try:
        return _run(split_name, index_name, run_date, students, index_path, profiles_dir, runs_dir, postings, materials,
                    embed_query, rerank_fn, model_name, warmup, cache)
    finally:
        config.CANDIDATE_K = old_k


def _run(split_name, index_name, run_date, students, index_path, profiles_dir, runs_dir, postings, materials,
         embed_query, rerank_fn, model_name, warmup, cache) -> Path:
    split = load_split()
    ids = students if students is not None else split[split_name]
    store = IndexStore(ensure_not_demo(index_path or INDEX_FILES[index_name]))
    entries = store.load()
    if not entries:
        raise SystemExit(f"색인이 비어 있어요: {store.path} (먼저 index 실행)")
    postings = postings if postings is not None else load_postings()
    if materials is None:
        materials = {int(m["studentId"]): m for m in json.loads(MATERIALS_FILE.read_text(encoding="utf-8"))}
    out_dir = runs_dir / (run_date or date.today().isoformat())

    if cache is not None:
        from matching.recommend import default_query_embedder, default_reranker

        embed_query = cache.embedder(default_query_embedder() if embed_query is _DEFAULT else embed_query)
        rerank_fn = cache.reranker(default_reranker() if rerank_fn is _DEFAULT else rerank_fn)
        if cache.only:
            warmup = None  # 캐시로만 돌면 리랭커 모델을 불러오지 않는다

    # 리랭커 첫 로딩 시간은 따로 잰다 (그 뒤 추천 시간에는 모델 로딩이 들어가지 않음)
    warmup_ms = None
    if warmup is _DEFAULT:
        from common.reranker import warmup as reranker_warmup

        warmup = reranker_warmup
    if warmup is not None:
        t0 = time.perf_counter()
        warmup()
        warmup_ms = int((time.perf_counter() - t0) * 1000)

    results, missing = [], []
    for sid in ids:
        profile_path = profiles_dir / f"student_{sid}.json"
        if not profile_path.exists():
            missing.append(sid)
            continue
        result = run_student(sid, json.loads(profile_path.read_text(encoding="utf-8")), materials.get(sid), store,
                             postings, embed_query=embed_query, rerank_fn=rerank_fn, model_name=model_name)
        if cache is not None and cache.missed:
            raise SystemExit(f"캐시에 없는 값이 필요해 멈췄어요 (student {sid}). --cache 로 먼저 한 번 실행하세요.")
        result = {**result, "split": split_name, "index": index_name}
        write_json(out_dir / f"student_{sid}.json", result)
        results.append(result)

    times = [r["timing"]["totalMs"] for r in results]
    summary = {
        "split": split_name,
        "index": index_name,
        "indexCount": len(entries),
        "date": out_dir.name,
        "commit": git_commit(),
        "config": {"candidateK": config.CANDIDATE_K, "scoreThreshold": config.SCORE_THRESHOLD,
                   "regionPolicy": config.REGION_POLICY,
                   "embeddingModel": default_model_name() if model_name is _DEFAULT else model_name},
        "warmupMs": warmup_ms,
        "students": len(results),
        "missingProfiles": missing,
        "timing": {"p50Ms": percentile(times, 0.5), "p95Ms": percentile(times, 0.95), "maxMs": max(times, default=None),
                   "meanMs": int(statistics.mean(times)) if times else None},
        "paths": {p: sum(r["path"] == p for r in results) for p in ("OK", "FALLBACK", "EMPTY")},
        "queryEmbeddingCalls": sum(r["timing"]["embedMs"] is not None for r in results),
        # 캐시를 썼으면 실제로 부른 수 (임베딩 호출 · 리랭커가 새로 점수 매긴 쌍)
        "cache": None if cache is None else {"only": cache.only, **cache.calls},
    }
    write_json(out_dir / f"_run_{split_name}.json", summary)

    print(f"{split_name} · {index_name}({len(entries)}건): {len(results)}명 저장 → {out_dir}")
    print(f"  리랭커 로딩 {warmup_ms}ms · 1명당 p50 {summary['timing']['p50Ms']}ms · p95 {summary['timing']['p95Ms']}ms"
          f" · K={config.CANDIDATE_K}")
    if cache is not None:
        print(f"  캐시: 임베딩 호출 {cache.calls['embedding']}번 · 리랭커 새 쌍 {cache.calls['rerankPairs']}개"
              + (" (캐시만)" if cache.only else ""))
    if missing:
        print(f"  프로필 없음 {len(missing)}명 (profiles 먼저 실행)")
    if split_name == "dev":
        print(f"  경로 {summary['paths']}")
        for r in results:
            print_dev_result(r)
    return out_dir


def print_dev_result(r: dict) -> None:
    s = r["student"]
    head = f"  [{r['studentId']}] {s['desiredJob']} · {s['preferredRegion']} · {r['timing']['totalMs']}ms · {r['path']}"
    print(head + (f" ({r['emptyReason'] or r['fallbackReason']})" if r["path"] != "OK" else ""))
    for it in r["items"]:
        score = it["rerankScore"]
        print(f"     {it['rank']}. {it['jobPostingId']} {it['title']} · {it['sido']} · {it['career']} · {it['education']}"
              f" · {it['sourceFile']} · score {score if score is None else round(score, 3)}")


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m matching.eval_run")
    sub = parser.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("profiles")
    p.add_argument("--dry-run", action="store_true")
    i = sub.add_parser("index")
    i.add_argument("--dry-run", action="store_true")
    r = sub.add_parser("run")
    r.add_argument("--split", choices=["dev", "test"], required=True)
    r.add_argument("--index", choices=sorted(INDEX_FILES), required=True)
    r.add_argument("--date", help="결과 폴더 이름 (기본: 오늘 YYYY-MM-DD)")
    r.add_argument("--cache", action="store_true", help="임베딩 · 리랭커 점수를 캐시에 저장하고 다시 쓴다")
    r.add_argument("--cache-only", action="store_true", help="캐시로만 실행 (없으면 멈춤, Gemini · 리랭커 호출 없음)")
    r.add_argument("--candidate-k", type=int, help="리랭커 후보 수 (기본 config.CANDIDATE_K)")
    args = parser.parse_args(argv)

    logging.basicConfig(level=logging.WARNING, stream=sys.stderr)
    if args.cmd == "profiles":
        cmd_profiles(args.dry_run)
    elif args.cmd == "index":
        cmd_index(args.dry_run)
    else:
        cache = None
        if args.cache or args.cache_only:
            cache = ScoreCache(CACHE_FILE, embedding_model=default_model_name() or "", reranker_model=default_reranker_model(),
                               only=args.cache_only)
        cmd_run(args.split, args.index, run_date=args.date, cache=cache, candidate_k=args.candidate_k)


if __name__ == "__main__":
    main()
