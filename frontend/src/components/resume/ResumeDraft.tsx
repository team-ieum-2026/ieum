"use client";
// 주인: FE-B — M6 이력서 초안 화면 본체 (FE-B-02). 섹션 카드 9개 + 자기소개서 미리보기 + 검토 요청 버튼
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import BottomSheet from "@/components/resume/BottomSheet";
import SaveStatus, { type SaveState } from "@/components/resume/SaveStatus";
import { SectionCard, SectionCardGroup } from "@/components/resume/SectionCard";
import SectionDetailSheet, { type DetailSection } from "@/components/resume/SectionDetailSheet";
import SectionEditSheet, { type ItemChange } from "@/components/resume/SectionEditSheet";
import SourceTag from "@/components/resume/SourceTag";
import { SECTION_FIELDS, isEditableSection, type EditableItem } from "@/components/resume/sectionFields";
import { summarizeSection, type CardSection, type SectionSummary } from "@/components/resume/sectionSummary";
import { ApiError } from "@/lib/api/client";
import { getApplication, patchDocument, submitApplication } from "@/lib/api/resume";
import type { ApplicationDetail, CoverLetterSection, DocumentPatchRequest, DocumentSection } from "@/types/resume";

const DETAIL_SECTIONS: DocumentSection["section"][] = ["BASIC", "MILITARY", "SKILLS", "PROJECTS", "PORTFOLIO"];
const isDetailSection = (s: DocumentSection): s is DetailSection => DETAIL_SECTIONS.includes(s.section);

const primaryButton =
  "flex min-h-[52px] flex-[2] items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";
const secondaryButton =
  "flex min-h-[52px] flex-1 items-center justify-center rounded-xl border border-line bg-surface text-[15px] font-bold text-ink";

export default function ResumeDraft({ applicationId }: { applicationId: number }) {
  const router = useRouter();
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  // notFound: 404 APPLICATION_NOT_FOUND — 다시 불러와도 같으므로 "다시 시도" 대신 지원 현황으로 보낸다
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // 섹션 편집 저장(PATCH) 중에는 SAVING, 실패하면 ERROR
  const [saveState, setSaveState] = useState<SaveState>("SAVED");
  const [editing, setEditing] = useState<SectionSummary | null>(null);
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // 화면이 다시 그려지기 전 두 번 눌러도 요청이 한 번만 가도록
  const submittingRef = useRef(false);

  useEffect(() => {
    let ignore = false;
    getApplication(applicationId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        setLoadError(
          e instanceof ApiError
            ? { message: e.message, notFound: e.code === "APPLICATION_NOT_FOUND" }
            : { message: "서류를 불러오지 못했어요.", notFound: false },
        );
      });
    return () => {
      ignore = true;
    };
  }, [applicationId, reloadKey]);

  const retry = () => {
    setLoadError(null);
    setReloadKey((k) => k + 1);
  };

  const retryButton = (
    <button type="button" onClick={retry} className="font-bold text-primary">
      다시 시도
    </button>
  );

  if (!detail) {
    return loadError ? (
      <CenterNotice
        message={loadError.message}
        action={
          loadError.notFound ? (
            <Link href="/student/applications" className="font-bold text-primary">
              지원 현황으로 가기
            </Link>
          ) : (
            retryButton
          )
        }
      />
    ) : (
      <CenterNotice message="서류를 불러오는 중이에요…" />
    );
  }

  const { status, job, document } = detail;
  const progressLink = (
    <Link href={`/student/applications/${applicationId}/progress`} className="font-bold text-primary">
      진행 화면으로 가기
    </Link>
  );

  // 서류가 아직 없거나 만들지 못한 상태 — M5(진행 화면)에서 진행 · 다시 시도 (FE-A-11)
  if (status === "GENERATING") return <CenterNotice message="서류를 만들고 있어요." action={progressLink} />;
  if (status === "FAILED") return <CenterNotice message="서류를 만들지 못했어요." action={progressLink} />;
  // document 는 GENERATING · FAILED 에서만 null (위에서 처리). 그 밖의 상태에서 비어 와도 화면이 멈추지 않도록 막는다
  if (!document || !Array.isArray(document.sections)) {
    return <CenterNotice message="서류 내용을 불러오지 못했어요." action={retryButton} />;
  }

  // DRAFT 일 때만 제출 가능. 그 밖의 상태에서 제출하면 409 INVALID_STATUS (docs/API.md 2-1)
  const canSubmit = status === "DRAFT";

  // FE-B-03: POST /api/applications/{id}/submit → 성공하면 M7(지원 현황)로 이동
  const submit = async () => {
    if (!canSubmit || submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await submitApplication(applicationId);
      router.push("/student/applications");
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setSubmitError(e instanceof ApiError ? e.message : "검토 요청을 보내지 못했어요.");
      // 이미 제출된 서류(409 INVALID_STATUS)면 최신 상태를 다시 불러와 버튼을 비활성화
      if (e instanceof ApiError && e.code === "INVALID_STATUS") setReloadKey((k) => k + 1);
      submittingRef.current = false;
      setSubmitting(false);
    }
  };

  const closeSubmitSheet = () => {
    if (submitting) return;
    setConfirmingSubmit(false);
    setSubmitError(null);
  };

  // FE-B-05: 학력 · 경력 · 자격증은 목록 편집 시트, 기본정보 · 병역 · 기술 · 경험 · 포트폴리오는 섹션 시트,
  // 교육(TRAINING)은 읽기 전용 안내 시트. 시트에는 응답으로 갱신된 최신 섹션을 넘긴다
  const editSection = editing ? document.sections.find((s) => s.section === editing.section) : undefined;
  const listTarget = editing && isEditableSection(editing.section) ? editing.section : null;
  const listItems: EditableItem[] = editSection && "items" in editSection ? (editSection.items as EditableItem[]) : [];
  const detailSection = editSection && isDetailSection(editSection) ? editSection : null;

  // 한 건 저장 → 응답의 서류 전체로 바꿔 카드 요약 · 출처 태그도 함께 갱신. 실패는 시트가 받아 입력값을 유지한다
  const saveItem = async (change: ItemChange, saveToProfile: boolean) => {
    if (!editing) return;
    // UPDATE_SENTENCE 는 sentenceId · text 만 보낸다 (docs/API.md 2-1)
    const body: DocumentPatchRequest =
      change.op === "UPDATE_SENTENCE" ? change : { ...change, section: editing.section, saveToProfile };
    setSaveState("SAVING");
    try {
      const result = await patchDocument(applicationId, body);
      setDetail((prev) => (prev ? { ...prev, document: result.document } : prev));
      setSaveState("SAVED");
    } catch (e: unknown) {
      setSaveState("ERROR");
      // 그사이 검토 요청이 된 서류(409 INVALID_STATUS)면 최신 상태를 다시 불러와 시트를 읽기 전용으로
      if (e instanceof ApiError && e.code === "INVALID_STATUS") setReloadKey((k) => k + 1);
      throw e;
    }
  };

  // 실패한 변경은 시트를 닫으면 버려지므로, 화면에 남은 내용은 저장된 상태와 같다
  const closeEditing = () => {
    setEditing(null);
    setSaveState((s) => (s === "ERROR" ? "SAVED" : s));
  };
  const cards = document.sections
    .filter((s): s is CardSection => s.section !== "COVER_LETTER")
    .map(summarizeSection);
  // 포트폴리오는 같은 카드 · 데이터를 쓰되 화면에서만 따로 묶어 보여 준다
  const resumeCards = cards.filter((c) => c.section !== "PORTFOLIO");
  const portfolioCard = cards.find((c) => c.section === "PORTFOLIO");
  const coverLetter = document.sections.find((s): s is CoverLetterSection => s.section === "COVER_LETTER");

  return (
    <div className="min-h-screen bg-surface-warm">
      <header className="fixed left-1/2 top-0 z-20 flex h-14 w-[390px] -translate-x-1/2 items-center justify-between gap-2 border-b border-line bg-surface-warm pl-2 pr-4">
        <div className="flex min-w-0 items-center gap-1">
          <button
            type="button"
            onClick={() => router.back()}
            aria-label="뒤로"
            className="flex size-11 shrink-0 items-center justify-center text-ink"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m15 18-6-6 6-6" />
            </svg>
          </button>
          <div className="flex min-w-0 flex-col">
            <h1 className="text-[15px] font-bold">이력서 초안</h1>
            <span className="truncate text-xs text-ink-sub">
              {job.companyName} · {job.title}
            </span>
          </div>
        </div>
        <SaveStatus state={saveState} />
      </header>

      <div className="flex flex-col px-4 pb-[104px] pt-[68px]">
        <p className="mx-1 text-xs text-ink-sub">AI가 준비한 초안이에요. 필요한 내용만 수정해 주세요.</p>
        {/* 출처 태그 설명: 이력서 · 포트폴리오 · 자기소개서 모두에 적용되는 화면 공통 설명 */}
        <div className="mx-0.5 mt-1.5 flex flex-nowrap items-center gap-x-2 whitespace-nowrap text-[10px] tracking-tight text-ink-sub">
          <span className="flex items-center gap-1">
            <SourceTag source="AI" /> AI가 근거로 정리
          </span>
          <span className="flex items-center gap-1">
            <SourceTag source="REGISTERED" /> 수료 시 등록
          </span>
          <span className="flex items-center gap-1">
            <SourceTag source="USER" /> 내가 쓰거나 고침
          </span>
        </div>

        <h2 className="mx-1 mb-2 mt-4 text-sm font-bold">이력서</h2>
        {/* 카드 순서 = API sections 순서 (기본정보 → 학력 → 교육 → 경력 → 병역 → 자격증 → 기술 → 경험) */}
        <SectionCardGroup>
          {resumeCards.map((card) => (
            <SectionCard key={card.section} summary={card} onOpen={setEditing} />
          ))}
        </SectionCardGroup>

        {portfolioCard && (
          <>
            <h2 className="mx-1 mb-2 mt-4 text-sm font-bold">포트폴리오</h2>
            <SectionCardGroup>
              <SectionCard summary={portfolioCard} onOpen={setEditing} />
            </SectionCardGroup>
          </>
        )}

        {coverLetter && <CoverLetterCard applicationId={applicationId} section={coverLetter} />}
      </div>

      <footer className="fixed bottom-0 left-1/2 z-20 flex w-[390px] -translate-x-1/2 flex-col gap-1 border-t border-line bg-surface px-5 pb-3 pt-2.5">
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => setConfirmingSubmit(true)}
          className="flex min-h-12 w-full items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:bg-line disabled:text-ink-muted"
        >
          담당자에게 검토 요청
        </button>
        <p className="text-center text-[11px] leading-4 text-ink-muted">
          {canSubmit
            ? "검토 후 기관 추천으로 전달돼요 · 기업에 바로 가지 않아요"
            : "이미 검토를 요청한 서류예요 · 지원 현황에서 확인할 수 있어요"}
        </p>
      </footer>

      {listTarget ? (
        <SectionEditSheet
          config={SECTION_FIELDS[listTarget]}
          items={listItems}
          editable={canSubmit}
          onChange={saveItem}
          onClose={closeEditing}
        />
      ) : detailSection ? (
        <SectionDetailSheet section={detailSection} editable={canSubmit} onChange={saveItem} onClose={closeEditing} />
      ) : (
        <BottomSheet
          open={editing !== null}
          title={editing?.label ?? ""}
          description="수료한 교육 과정 정보라 여기서 고칠 수 없어요."
          onClose={() => setEditing(null)}
          actions={
            <button type="button" onClick={() => setEditing(null)} className={secondaryButton}>
              닫기
            </button>
          }
        >
          <p className="mt-4 rounded-xl border border-line px-3 py-3 text-sm">{editing?.text}</p>
        </BottomSheet>
      )}

      <BottomSheet
        open={confirmingSubmit && canSubmit}
        title="담당자에게 서류를 보낼까요?"
        description={`${job.companyName} · ${job.title} 지원 서류가 담당 코디네이터 검토 단계로 넘어가요. 기업에 바로 보내지는 않아요.`}
        onClose={closeSubmitSheet}
        actions={
          <>
            <button type="button" onClick={closeSubmitSheet} disabled={submitting} className={secondaryButton}>
              취소
            </button>
            <button type="button" onClick={submit} disabled={submitting} aria-busy={submitting} className={primaryButton}>
              {submitting ? "보내는 중…" : "검토 요청"}
            </button>
          </>
        }
      >
        {submitError && (
          <p role="alert" className="mt-3 text-[13px] text-danger">
            {submitError}
          </p>
        )}
      </BottomSheet>
    </div>
  );
}

/** 불러오는 중 · 오류 · 서류 없음 안내 (화면 가운데) */
function CenterNotice({ message, action }: { message: string; action?: React.ReactNode }) {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-sm text-ink-sub">
      <p>{message}</p>
      {action}
    </div>
  );
}

function CoverLetterCard({ applicationId, section }: { applicationId: number; section: CoverLetterSection }) {
  const aiCount = section.sentences.filter((s) => s.source === "AI").length;
  const userCount = section.sentences.length - aiCount;

  return (
    <>
      {/* 카드 안에 "자기소개서" 제목이 있어 바깥 제목은 두지 않는다 */}
      <Link
        href={`/student/applications/${applicationId}/cover-letter`}
        className="mt-4 flex flex-col gap-1.5 rounded-2xl border-[1.5px] border-primary bg-surface px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span className="flex flex-wrap items-center gap-1.5">
          <b className="mr-auto text-sm">자기소개서</b>
          {aiCount > 0 && <SourceTag source="AI" label={`AI 작성 ${aiCount}문장`} />}
          {userCount > 0 && <SourceTag source="USER" label={`직접 입력 ${userCount}문장`} />}
        </span>
        <span className="-mt-1 text-[11px] text-ink-muted">공고에 맞춰 AI가 추천 작성한 내용이에요.</span>
        {section.sentences.length > 0 ? (
          // 문장을 이어 붙여 최대 3줄, 넘치면 말줄임표. AI 문장은 밑줄 강조
          <span className="line-clamp-3 text-[13px] leading-[1.6] text-ink-sub">
            {section.sentences.map((s, i) => (
              <span key={s.sentenceId}>
                {i > 0 && " "}
                <span className={s.source === "AI" ? "border-b-2 border-primary bg-primary-tint" : undefined}>
                  {s.text}
                </span>
              </span>
            ))}
          </span>
        ) : (
          <span className="text-[13px] text-ink-muted">아직 작성된 문장이 없어요</span>
        )}
        <span className="text-xs font-bold text-primary">전체 보기 · 근거 확인 · 수정 ›</span>
      </Link>
    </>
  );
}
