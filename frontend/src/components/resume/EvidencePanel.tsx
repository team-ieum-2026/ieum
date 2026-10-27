// 주인: FE-B — 선택한 문장의 근거 (FE-B-01). 출처(label) · 원문(quote) · URL · 수집일(collectedAt). M6-1 · C4 공용
// 근거 형식은 docs/API.md 3-2. url · collectedAt 은 기업 정보 근거에만 채워지고 나머지는 null.
import { toDate } from "@/components/resume/resumeLabels";
import type { Sentence } from "@/types/resume";

interface EvidencePanelProps {
  /** null 이면 "문장을 선택하면…" 안내 */
  sentence: Sentence | null;
  /** true 면 테두리 · 배경 · 안쪽 여백 없이 (다른 카드 안에 넣을 때). 기본은 초록 테두리 카드 */
  bare?: boolean;
  /** 본문에 붙인 문장 번호 (예: "AI 2") → 제목 "AI 2의 근거 · 출처". 없으면 "근거 1 · 출처" */
  marker?: string;
}

export default function EvidencePanel({ sentence, bare = false, marker }: EvidencePanelProps) {
  const title = (index: number, total: number) =>
    marker ? `${marker}의 근거${total > 1 ? ` ${index + 1}` : ""}` : `근거 ${index + 1}`;
  return (
    <section
      aria-live="polite"
      aria-label="선택한 문장의 근거"
      className={`flex flex-col gap-2 ${bare ? "" : "rounded-xl border-[1.5px] border-primary bg-surface p-3"}`}
    >
      {!sentence ? (
        <p className="text-[13px] text-ink-muted">문장을 선택하면 근거가 보여요.</p>
      ) : sentence.evidence.length === 0 ? (
        // USER 문장은 근거가 비어 있어도 된다 (docs/API.md 3-3)
        <p className="text-[13px] text-ink-muted">
          {sentence.source === "USER" ? "직접 입력한 문장이라 연결된 근거가 없어요." : "연결된 근거가 없어요."}
        </p>
      ) : (
        <ol className="flex flex-col gap-3">
          {sentence.evidence.map((ev, i) => (
            <li key={`${ev.refType}-${ev.refId}-${i}`} className="flex flex-col gap-1">
              <span className="text-[11px] font-bold text-primary">
                {title(i, sentence.evidence.length)} · {ev.label}
              </span>
              <blockquote className="text-[13px] leading-relaxed text-ink-sub">{ev.quote}</blockquote>
              {(ev.url || ev.collectedAt) && (
                <span className="flex flex-wrap gap-x-2 text-[11px] text-ink-muted">
                  {ev.url && (
                    <a
                      href={ev.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="break-all text-primary underline"
                    >
                      {ev.url}
                    </a>
                  )}
                  {ev.collectedAt && <span>수집일 {toDate(ev.collectedAt)}</span>}
                </span>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
