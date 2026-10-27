# 주인: AI-B
"""공고 색인 저장소: ai/data/index/jobs_index.json

{ "<jobPostingId>": { contentHash, preprocessVersion, embeddingModel, matchText, careerRequired, region, minEducation, vector, indexedAt } }
JSON 키는 문자열이라 jobPostingId 도 문자열로 저장한다. 쓰기는 같은 폴더의 임시 파일에 쓴 뒤 교체한다(중간에 죽어도 기존 파일이 깨지지 않게).
"""

import json
import os
import tempfile
import threading
from pathlib import Path
from typing import Any

DEFAULT_PATH = Path(__file__).resolve().parent.parent / "data" / "index" / "jobs_index.json"

Entry = dict[str, Any]


class IndexStore:
    def __init__(self, path: Path = DEFAULT_PATH):
        self.path = Path(path)
        # 읽기 → 고치기 → 쓰기 를 한 번에 하나만 (BE 배치가 겹쳐 호출돼도 덮어쓰지 않게)
        self.lock = threading.Lock()

    def load(self) -> dict[str, Entry]:
        if not self.path.exists():
            return {}
        return json.loads(self.path.read_text(encoding="utf-8"))

    def save(self, entries: dict[str, Entry]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp = tempfile.mkstemp(dir=self.path.parent, prefix=self.path.name, suffix=".tmp")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as f:
                json.dump(entries, f, ensure_ascii=False)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.path)
        except BaseException:
            Path(tmp).unlink(missing_ok=True)
            raise
