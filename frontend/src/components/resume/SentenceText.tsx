// 주인: FE-B — 서류 문장 하나 (FE-B-01). AI 문장 강조 · 직접 입력 표시 · 클릭 · 키보드 선택. M6-1 · C4 공용
// 문단 안에서 줄바꿈되도록 인라인(span)으로 그리고, onSelect 가 있으면 버튼처럼 동작한다.
import type { Sentence } from "@/types/resume";

interface SentenceTextProps {
  sentence: Sentence;
  selected?: boolean;
  /** 없으면 읽기 전용 (강조만) */
  onSelect?: (sentence: Sentence) => void;
  /** 문장 끝 작은 표시 (예: "AI 1"). USER 문장은 항상 "직접"을 붙이므로 무시 */
  marker?: string;
  /** 수료생 문서에서는 본문만 표시하고 출처는 문서의 토글·호버로 구분한다. */
  presentation?: "highlighted" | "document";
}

const SOURCE_STYLE: Record<Sentence["source"], string> = {
  AI: "border-b-2 border-primary bg-primary-tint",
  USER: "border-b-2 border-warn-ink bg-warn-bg",
  REGISTERED: "",
};

const SOURCE_TEXT: Record<Sentence["source"], string> = {
  AI: "AI 작성",
  USER: "직접 입력",
  REGISTERED: "등록 자료",
};

export default function SentenceText({ sentence, selected = false, onSelect, marker, presentation = "highlighted" }: SentenceTextProps) {
  if (presentation === "document") {
    const label = sentence.source === "USER" ? "직접 작성·수정" : SOURCE_TEXT[sentence.source];
    return <span
      className="s-document-sentence"
      data-sentence-id={sentence.sentenceId}
      data-source={sentence.source}
      role={onSelect ? "button" : undefined}
      tabIndex={onSelect ? 0 : undefined}
      aria-pressed={onSelect ? selected : undefined}
      onClick={onSelect ? () => onSelect(sentence) : undefined}
      onKeyDown={onSelect ? event => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect(sentence);
        }
      } : undefined}
    ><span className="sr-only">{label}: </span><span className="s-document-sentence-text">{sentence.text}</span></span>;
  }
  const style = selected && sentence.source === "AI" ? "border-b-2 border-primary bg-primary-soft" : SOURCE_STYLE[sentence.source];
  const base = `box-decoration-clone px-0.5 ${style}`;
  const content = (
    <>
      {sentence.text}
      {/* 색만으로 출처를 구분하지 않도록 화면 읽기 프로그램용 문구를 붙인다 */}
      <span className="sr-only"> ({SOURCE_TEXT[sentence.source]})</span>
      {sentence.source === "USER" ? (
        <span className="ml-0.5 align-super text-[10px] font-bold text-warn-ink" aria-hidden="true">
          직접
        </span>
      ) : (
        marker && (
          <span className="ml-0.5 align-super text-[10px] font-bold text-primary" aria-hidden="true">
            {marker}
          </span>
        )
      )}
    </>
  );

  if (!onSelect) return <span className={base}>{content}</span>;

  return (
    <span
      role="button"
      tabIndex={0}
      aria-pressed={selected}
      onClick={() => onSelect(sentence)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onSelect(sentence);
        }
      }}
      className={`${base} cursor-pointer rounded-sm focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary ${selected ? "ring-1 ring-primary" : ""}`}
    >
      {content}
    </span>
  );
}
