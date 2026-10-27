"use client";
// 주인: FE-B — M6-1 자기소개서 편집 화면 본체 (FE-B-04)
// 문장 선택 → 근거(EvidencePanel) · 문장 고치기 → PATCH UPDATE_SENTENCE. 응답의 서류 전체로 화면을 바꾼다 (docs/API.md 2-1)
// 와이어프레임 M6-1: 본문은 배경 위 문단, 근거 · 고치기 창은 선택한 문장 바로 아래에 끼워 넣고 다음 문장이 그 아래로 이어진다.
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import EvidencePanel from "@/components/resume/EvidencePanel";
import SentenceText from "@/components/resume/SentenceText";
import SaveStatus, { type SaveState } from "@/components/resume/SaveStatus";
import { ApiError } from "@/lib/api/client";
import { getApplication, patchDocument } from "@/lib/api/resume";
import type { ApplicationDetail, ApplicationDocument, CoverLetterSection, Sentence } from "@/types/resume";
import type { CompanyInfoStatus } from "@/types/student";

const findSentence = (document: ApplicationDocument, sentenceId: string) =>
  document.sections
    .find((s): s is CoverLetterSection => s.section === "COVER_LETTER")
    ?.sentences.find((s) => s.sentenceId === sentenceId) ?? null;

export default function CoverLetterEditor({ applicationId }: { applicationId: number }) {
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [saveState, setSaveState] = useState<SaveState>("SAVED");
  const [saveError, setSaveError] = useState<string | null>(null);
  const savingRef = useRef(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);
  // 마지막 입력이 키보드였는지 (마우스 선택 때는 포커스 테두리를 다시 띄우지 않는다)
  const keyboardRef = useRef(false);

  useEffect(() => {
    let ignore = false;
    getApplication(applicationId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((e: unknown) => {
        if (!ignore) setLoadError(e instanceof ApiError ? e.message : "서류를 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [applicationId, reloadKey]);

  const backHref = `/student/applications/${applicationId}`;

  if (!detail) {
    return (
      <Notice message={loadError ?? "자기소개서를 불러오는 중이에요…"}>
        {loadError && (
          <button
            type="button"
            onClick={() => {
              setLoadError(null);
              setReloadKey((k) => k + 1);
            }}
            className="font-bold text-primary"
          >
            다시 시도
          </button>
        )}
      </Notice>
    );
  }

  const { status, job, document } = detail;
  // API 명세상 document 는 항상 오지만, 비어 오면 화면이 멈추지 않도록 막는다
  if (!document || !Array.isArray(document.sections)) {
    return (
      <Notice message="자기소개서를 불러오지 못했어요.">
        <Link href={backHref} className="font-bold text-primary">
          이력서로 돌아가기
        </Link>
      </Notice>
    );
  }

  // 제출 뒤 수정 가능 여부는 명세에 없어 DRAFT 일 때만 고칠 수 있게 둔다 (BE-A 확인 필요)
  const editable = status === "DRAFT";
  const selected = selectedId ? findSentence(document, selectedId) : null;
  const sentences =
    document.sections.find((s): s is CoverLetterSection => s.section === "COVER_LETTER")?.sentences ?? [];
  // 선택한 문장까지 앞 문단, 그다음 문장부터 뒤 문단 → 사이에 근거 · 고치기 창
  const cut = selected ? sentences.findIndex((s) => s.sentenceId === selected.sentenceId) + 1 : sentences.length;
  const head = sentences.slice(0, cut);
  const tail = sentences.slice(cut);
  // 근거가 연결된 AI 문장에만 순서대로 "AI 1", "AI 2"… (문단이 나뉘어도 번호는 전체 기준)
  const markers = new Map<string, string>();
  sentences
    .filter((s) => s.source === "AI" && s.evidence.length > 0)
    .forEach((s, i) => markers.set(s.sentenceId, `AI ${i + 1}`));
  const trimmed = draft.trim();
  const canApply = editable && selected !== null && trimmed !== "" && trimmed !== selected.text && saveState !== "SAVING";

  const select = (sentence: Sentence) => {
    setSelectedId(sentence.sentenceId);
    setDraft(sentence.text);
    setSaveError(null);
    // 고치던 내용은 버려지고 서류는 마지막 저장 상태 그대로다
    if (saveState === "ERROR") setSaveState("SAVED");
    requestAnimationFrame(() => {
      // 선택한 문장이 앞 문단으로 옮겨 그려지며 포커스를 잃으므로 키보드 사용 중이면 되돌린다
      if (keyboardRef.current) {
        bodyRef.current?.querySelector<HTMLElement>('[aria-pressed="true"]')?.focus({ preventScroll: true });
      }
      // 모바일에서 끼워 넣은 창이 화면 밖으로 나가지 않도록 (헤더 · 하단 버튼 높이는 scroll-margin 으로 뺀다)
      panelRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
  };

  const cancel = () => {
    setSelectedId(null);
    setSaveError(null);
    if (saveState === "ERROR") setSaveState("SAVED");
  };

  const apply = async () => {
    if (!canApply || !selected || savingRef.current) return;
    savingRef.current = true;
    setSaveState("SAVING");
    setSaveError(null);
    try {
      const result = await patchDocument(applicationId, {
        op: "UPDATE_SENTENCE",
        sentenceId: selected.sentenceId,
        text: trimmed,
      });
      // 서버가 돌려준 서류 전체로 교체 → 고친 문장은 source USER("직접 입력")로 바뀐다
      setDetail({ ...detail, document: result.document });
      const updated = findSentence(result.document, selected.sentenceId);
      setDraft(updated?.text ?? trimmed);
      setSaveState("SAVED");
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setSaveError(e instanceof ApiError ? e.message : "문장을 저장하지 못했어요.");
      setSaveState("ERROR");
    } finally {
      savingRef.current = false;
    }
  };

  return (
    <div className="min-h-screen bg-surface-warm">
      <header className="fixed left-1/2 top-0 z-20 flex h-14 w-[390px] -translate-x-1/2 items-center justify-between gap-2 border-b border-line bg-surface-warm pl-2 pr-4">
        <div className="flex min-w-0 items-center gap-1">
          <Link href={backHref} aria-label="이력서로 돌아가기" className="flex size-11 shrink-0 items-center justify-center text-ink">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m15 18-6-6 6-6" />
            </svg>
          </Link>
          <div className="flex min-w-0 flex-col">
            <h1 className="text-[15px] font-bold">자기소개서</h1>
            <span className="truncate text-xs text-ink-sub">
              {job.companyName} · {job.title}
            </span>
          </div>
        </div>
        <SaveStatus state={saveState} />
      </header>

      <div
        ref={bodyRef}
        onKeyDownCapture={() => {
          keyboardRef.current = true;
        }}
        onPointerDownCapture={() => {
          keyboardRef.current = false;
        }}
        className="flex flex-col gap-3 px-5 pb-[104px] pt-[68px]"
      >
        <CompanyInfoNotice status={document.companyInfoStatus} sentences={sentences} />
        {/* 안내: 배경 없이 회색 세로선 + 정보 아이콘 (AI 초록 · 직접 입력 노랑과 겹치지 않는 중립 토큰) */}
        <div className="flex items-start gap-2 border-l-2 border-ink-muted py-0.5 pl-2.5">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="mt-0.5 shrink-0 text-ink-sub">
            <circle cx="12" cy="12" r="10" />
            <path d="M12 16v-4M12 8h.01" />
          </svg>
          <p className="flex flex-col">
            <b className="text-xs text-ink">{editable ? "수정할 문장을 눌러보세요." : "문장을 눌러보세요."}</b>
            <span className="text-[11px] text-ink-sub">AI 작성 문장은 근거도 함께 확인할 수 있어요.</span>
          </p>
        </div>

        {sentences.length === 0 && <p className="text-sm text-ink-muted">아직 작성된 문장이 없어요</p>}
        {head.length > 0 && (
          <SentenceParagraph sentences={head} markers={markers} selectedId={selectedId} onSelect={select} />
        )}

        {selected && (
          // 와이어프레임 M6-1: 근거 → 문장 고치기 → 취소 · 적용을 초록 테두리 카드 하나에
          <div
            ref={panelRef}
            className="flex scroll-mb-28 scroll-mt-16 flex-col gap-2 rounded-xl border-[1.5px] border-primary bg-surface p-3"
          >
            {/* 직접 입력 문장은 근거가 없으므로 고칠 수 있을 때는 근거 안내를 빼고 입력창만 */}
            {(selected.source !== "USER" || !editable) && (
              <EvidencePanel sentence={selected} bare marker={markers.get(selected.sentenceId)} />
            )}
            {editable && (
              <>
                <label className={`${selected.source === "USER" ? "" : "mt-1 "}flex flex-col gap-1 text-xs text-ink-sub`}>
                  문장 고치기
                  <textarea
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    rows={3}
                    className="resize-none rounded-[10px] border-[1.5px] border-line p-2.5 text-sm text-ink focus-visible:outline-2 focus-visible:outline-primary"
                  />
                </label>
                {selected.source !== "USER" && (
                  <span className="text-[11px] text-ink-muted">고친 문장은 근거 표시 대신 &apos;직접 입력&apos;으로 표시돼요</span>
                )}
                {saveError && (
                  <p role="alert" className="text-[13px] text-danger">
                    {saveError}
                  </p>
                )}
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={cancel}
                    disabled={saveState === "SAVING"}
                    className="min-h-10 flex-1 rounded-lg border-[1.5px] border-line bg-surface text-[13px] font-bold text-ink"
                  >
                    취소
                  </button>
                  <button
                    type="button"
                    onClick={apply}
                    disabled={!canApply}
                    aria-busy={saveState === "SAVING"}
                    className="min-h-10 flex-1 rounded-lg bg-primary text-[13px] font-bold text-surface disabled:bg-line disabled:text-ink-muted"
                  >
                    {saveState === "SAVING" ? "저장 중…" : "적용"}
                  </button>
                </div>
              </>
            )}
          </div>
        )}

        {tail.length > 0 && (
          <SentenceParagraph sentences={tail} markers={markers} selectedId={selectedId} onSelect={select} />
        )}
      </div>

      <footer className="fixed bottom-0 left-1/2 z-20 w-[390px] -translate-x-1/2 border-t border-line bg-surface px-5 pb-5 pt-3">
        <Link
          href={backHref}
          className="flex min-h-12 w-full items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          완료
        </Link>
      </footer>
    </div>
  );
}

/**
 * 기업 정보 반영 범위 안내 (FE-B-04). PARTIAL · NOT_FOUND 일 때만 (docs/API.md 3-3)
 * 팀 결정으로 두 상태는 화면에서 같은 문구로 보여 준다. 상태값 자체는 그대로 쓴다.
 * 작성 기준은 고정 문구가 아니라 자기소개서 문장에 실제 연결된 기업 정보 근거(COMPANY_INFO)의 출처로 만든다.
 */
function CompanyInfoNotice({ status, sentences }: { status: CompanyInfoStatus; sentences: Sentence[] }) {
  if (status === "FOUND") return null;

  const companyInfoLabels = [
    ...new Set(sentences.flatMap((s) => s.evidence).filter((e) => e.refType === "COMPANY_INFO").map((e) => e.label)),
  ];
  const content = {
    title: "확인된 기업 정보만 반영했어요",
    description: "채용공고와 신뢰성을 확인할 수 있는 기업 정보만 자기소개서 작성에 활용했어요.",
    basis: ["채용공고", ...companyInfoLabels].join(" · "),
  };

  return (
    <section role="note" className="flex items-start gap-2.5 rounded-[10px] border border-line bg-surface px-3 py-2.5">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="mt-px shrink-0 text-ink-sub">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
      <div className="flex min-w-0 flex-col gap-0.5">
        <b className="text-[13px] text-ink">{content.title}</b>
        <p className="text-xs leading-relaxed text-ink-sub">{content.description}</p>
        <p className="mt-0.5 text-[11px] text-ink-muted">작성 기준: {content.basis}</p>
      </div>
    </section>
  );
}

/** 배경 위에 이어지는 자기소개서 문단 (14px · 줄 간격 1.9) */
function SentenceParagraph({
  sentences,
  markers,
  selectedId,
  onSelect,
}: {
  sentences: Sentence[];
  markers: Map<string, string>;
  selectedId: string | null;
  onSelect: (sentence: Sentence) => void;
}) {
  return (
    <p className="text-sm leading-[1.9] text-ink">
      {sentences.map((s, i) => (
        <span key={s.sentenceId}>
          {i > 0 && " "}
          <SentenceText
            sentence={s}
            selected={s.sentenceId === selectedId}
            onSelect={onSelect}
            marker={markers.get(s.sentenceId)}
          />
        </span>
      ))}
    </p>
  );
}

function Notice({ message, children }: { message: string; children?: React.ReactNode }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-sm text-ink-sub">
      <p>{message}</p>
      {children}
    </div>
  );
}
