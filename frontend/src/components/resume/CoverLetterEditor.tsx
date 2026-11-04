"use client";
// 주인: FE-B — M6-1 자기소개서 편집 화면 본체 (FE-B-04)
// 문장 선택 → 근거(EvidencePanel) · 문장 고치기 → PATCH UPDATE_SENTENCE. 응답의 서류 전체로 화면을 바꾼다 (docs/API.md 2-1)
// M6-1: 본문과 선택 문장의 근거를 웹에서는 나란히, 모바일에서는 위아래로 표시한다.
import Link from "next/link";
import { Fragment, useEffect, useRef, useState } from "react";
import EvidencePanel from "@/components/resume/EvidencePanel";
import SourceTag from "@/components/resume/SourceTag";
import SentenceText from "@/components/resume/SentenceText";
import { groupCoverLetterParagraphs } from "@/components/resume/coverLetterParagraphs";
import StudentIcon from "@/components/student/StudentIcon";
import SaveStatus, { type SaveState } from "@/components/resume/SaveStatus";
import { studentHref } from "@/components/student/navigation";
import { ApiError } from "@/lib/api/client";
import { getApplication, patchDocument } from "@/lib/api/resume";
import type { ApplicationDetail, ApplicationDocument, CoverLetterSection, Sentence } from "@/types/resume";
import type { CompanyInfoStatus } from "@/types/student";
import { useApplicationBreadcrumb } from "@/components/student/StudentShell";

const findSentence = (document: ApplicationDocument, sentenceId: string) =>
  document.sections
    .find((s): s is CoverLetterSection => s.section === "COVER_LETTER")
    ?.sentences.find((s) => s.sentenceId === sentenceId) ?? null;

export default function CoverLetterEditor({ applicationId, studentId }: { applicationId: number; studentId: number }) {
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  useApplicationBreadcrumb(detail?.status);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showSources, setShowSources] = useState(false);
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
        if (!ignore) {
          setDetail(data);
          setSelectedId(null);
          setSaveState("SAVED");
          setSaveError(null);
          const firstSentence = data.document?.sections
            .find((section): section is CoverLetterSection => section.section === "COVER_LETTER")?.sentences[0];
          setDraft(firstSentence?.text ?? "");
        }
      })
      .catch((e: unknown) => {
        if (!ignore) setLoadError(e instanceof ApiError ? e.message : "서류를 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [applicationId, reloadKey]);

  const backHref = studentHref(`/student/applications/${applicationId}`, studentId);

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

  const { status, document } = detail;
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

  // 서버의 저장 규칙에 맞춰 검토 요청 전(DRAFT) 지원서만 수정한다.
  const editable = status === "DRAFT";
  const sentences =
    document.sections.find((s): s is CoverLetterSection => s.section === "COVER_LETTER")?.sentences ?? [];
  // 본문은 강조 없이 열고, 오른쪽에서는 첫 문장부터 바로 확인·수정할 수 있게 한다.
  const selected = (selectedId ? findSentence(document, selectedId) : null) ?? sentences[0] ?? null;
  const sourceCounts = (["AI", "USER", "REGISTERED"] as const).map(source => ({ source, count: sentences.filter(sentence => sentence.source === source).length }));
  const trimmed = draft.trim();
  const canApply = editable && selected !== null && trimmed !== "" && trimmed !== selected.text && saveState !== "SAVING";

  const select = (sentence: Sentence) => {
    if (savingRef.current) return;
    setSelectedId(sentence.sentenceId);
    setDraft(sentence.text);
    setSaveError(null);
    // 고치던 내용은 버려지고 서류는 마지막 저장 상태 그대로다
    if (saveState === "ERROR") setSaveState("SAVED");
    requestAnimationFrame(() => {
      // 키보드로 선택한 문장의 포커스를 유지한다.
      if (keyboardRef.current) {
        bodyRef.current?.querySelector<HTMLElement>('[aria-pressed="true"]')?.focus({ preventScroll: true });
      }
      // 좁은 화면에서만 근거 패널로 이동한다. 웹에서는 본문 스크롤 위치를 유지한다.
      if (window.matchMedia("(max-width: 1023px)").matches) {
        panelRef.current?.scrollIntoView({ behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth", block: "nearest" });
      }
    });
  };

  const cancel = () => {
    setDraft(selected?.text ?? "");
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
    <div className="s-cover-page">
      <div className="s-cover-toolbar s-cover-notice-row"><div><CompanyInfoNotice status={document.companyInfoStatus} sentences={sentences} /></div><Link href={backHref} className="s-button" aria-disabled={saveState === "SAVING"} onClick={event => { if (savingRef.current) event.preventDefault(); }}><StudentIcon name="arrow" className="s-cover-back-icon" />전체 검토 화면으로</Link></div>
      <div ref={bodyRef} onKeyDownCapture={() => { keyboardRef.current = true; }} onPointerDownCapture={() => { keyboardRef.current = false; }} className="s-cover-layout">
        <article className="s-panel s-letter-document" data-show-sources={showSources}>
          <header className="s-student-document-heading s-letter-document-heading"><h1>자기소개서</h1><SaveStatus state={saveState} /></header>
          <div className="s-letter-document-meta"><div className="s-letter-source-counts">{sourceCounts.filter(({ count }) => count > 0).map(({ source, count }) => <SourceTag key={source} source={source} label={`${source === "AI" ? "AI 작성" : source === "USER" ? "직접 작성·수정" : "등록 자료"} ${count}문장`} />)}</div><button type="button" role="switch" aria-checked={showSources} aria-controls="cover-letter-body" onClick={() => setShowSources(value => !value)} className="s-letter-source-toggle"><span>문장 출처 표시</span><span className="s-letter-switch-track" aria-hidden="true"><span /></span></button></div>
          {sentences.length ? <SentenceParagraph sentences={sentences} selectedId={selectedId} onSelect={select} /> : <p className="s-source-note">아직 작성된 문장이 없어요.</p>}
          <p className="s-source-note">AI 작성 문장은 등록 자료와 확인된 기업 정보에 연결된 근거를 살펴볼 수 있어요.</p>
        </article>
        <div ref={panelRef} className="s-panel s-letter-evidence" aria-busy={saveState === "SAVING"}><h2>문장 근거·출처</h2>
          {selected ? <>
            <div className="s-letter-selected-summary"><div className="s-letter-selection-tags"><SourceTag source={selected.source} label={selected.source === "USER" ? "직접 작성·수정" : undefined} /><span>{selected.evidence.length ? `근거 ${selected.evidence.length}개 연결` : selected.source === "USER" ? "직접 작성·수정한 내용" : "연결된 근거 없음"}</span></div><blockquote>{selected.text}</blockquote></div>
            <EvidencePanel sentence={selected} bare />
            <label className="flex flex-col gap-2 text-xs font-medium text-ink-sub">문장 고치기<textarea value={draft} onChange={event => setDraft(event.target.value)} disabled={!editable || saveState === "SAVING"} rows={7} className="w-full resize-y rounded-lg border border-line bg-surface p-3 text-sm leading-relaxed text-ink" /></label><p className="s-letter-note">{editable ? "고친 문장은 ‘직접 작성·수정’으로 표시돼요." : "매니저에게 검토를 요청한 서류는 수정할 수 없어요."}</p>{saveError && <p role="alert" className="text-sm text-danger">{saveError}</p>}<div className="flex gap-2"><button type="button" onClick={cancel} disabled={!editable || saveState === "SAVING"} className="s-button">취소</button><button type="button" onClick={apply} disabled={!canApply} aria-busy={saveState === "SAVING"} className="s-button s-button-primary">{saveState === "SAVING" ? "저장 중…" : "적용"}</button></div>
          </> : <EvidencePanel sentence={null} bare />}
        </div>
      </div>
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

/** 문장을 하나의 본문으로 이어 읽고, 선택한 문장을 근거 · 편집 패널에 연결한다. */
function SentenceParagraph({ sentences, selectedId, onSelect }: { sentences: Sentence[]; selectedId: string | null; onSelect: (sentence: Sentence) => void }) {
  return <div className="s-letter-sentences" id="cover-letter-body">{groupCoverLetterParagraphs(sentences).map(paragraph => <p key={paragraph[0].sentenceId}>{paragraph.map((sentence, index) => <Fragment key={sentence.sentenceId}>{index > 0 && " "}<SentenceText sentence={sentence} selected={selectedId === sentence.sentenceId} onSelect={onSelect} presentation="document" /></Fragment>)}</p>)}</div>;
}

function Notice({ message, children }: { message: string; children?: React.ReactNode }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-sm text-ink-sub">
      <p>{message}</p>
      {children}
    </div>
  );
}
