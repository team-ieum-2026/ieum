"use client";
// 주인: FE-B — M6 이력서 초안 화면 본체 (FE-B-02). 섹션 카드 9개 + 자기소개서 미리보기 + 검토 요청 버튼
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import BottomSheet from "@/components/resume/BottomSheet";
import SaveStatus, { type SaveState } from "@/components/resume/SaveStatus";
import { SectionCard, SectionCardGroup } from "@/components/resume/SectionCard";
import SourceTag from "@/components/resume/SourceTag";
import {
  OPTIONAL_SECTIONS,
  summarizeSection,
  type CardSection,
  type SectionSummary,
} from "@/components/resume/sectionSummary";
import { ApiError } from "@/lib/api/client";
import { getApplication } from "@/lib/api/resume";
import type { ApplicationDetail, CoverLetterSection } from "@/types/resume";

const primaryButton =
  "flex min-h-[52px] flex-[2] items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary";
const secondaryButton =
  "flex min-h-[52px] flex-1 items-center justify-center rounded-xl border border-line bg-surface text-[15px] font-bold text-ink";

export default function ResumeDraft({ applicationId }: { applicationId: number }) {
  const router = useRouter();
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // TODO(FE-B-05): 섹션 편집 저장(PATCH) 중에는 SAVING, 실패하면 ERROR 로 바꾼다
  const [saveState] = useState<SaveState>("SAVED");
  const [editing, setEditing] = useState<SectionSummary | null>(null);
  const [confirmingSubmit, setConfirmingSubmit] = useState(false);

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

  const retry = () => {
    setLoadError(null);
    setReloadKey((k) => k + 1);
  };

  if (!detail) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-sm text-ink-sub">
        {loadError ? (
          <>
            <p>{loadError}</p>
            <button type="button" onClick={retry} className="font-bold text-primary">
              다시 시도
            </button>
          </>
        ) : (
          <p>서류를 불러오는 중이에요…</p>
        )}
      </div>
    );
  }

  const { job, document } = detail;
  const cards = document.sections
    .filter((s): s is CardSection => s.section !== "COVER_LETTER")
    .map(summarizeSection);
  const requiredCards = cards.filter((c) => !OPTIONAL_SECTIONS.includes(c.section));
  const optionalCards = cards.filter((c) => OPTIONAL_SECTIONS.includes(c.section));
  const coverLetter = document.sections.find((s): s is CoverLetterSection => s.section === "COVER_LETTER");

  return (
    // 레이아웃 main 의 하단 메뉴 여백(pb-16)만큼 빼야 한 화면(844px)에서 스크롤이 생기지 않는다
    <div className="min-h-[calc(100dvh-4rem)] bg-surface-warm">
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

      <div className="flex flex-col px-4 pb-[94px] pt-16">
        <section className="mb-2.5 flex items-center gap-3 rounded-2xl border border-primary-soft bg-primary-tint px-3.5 py-2">
          <span
            className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-surface text-primary"
            aria-hidden="true"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <path d="M14 2v6h6M8 13h8M8 17h5" />
            </svg>
          </span>
          <div className="min-w-0">
            <p className="text-sm font-bold">AI가 이력서 초안을 준비했어요</p>
            <p className="mt-0.5 text-xs text-ink-sub">각 항목을 확인하고 필요한 내용만 고쳐 주세요.</p>
          </div>
        </section>

        <h2 className="mx-1 mb-1.5 text-sm font-bold">이력서</h2>
        <SectionCardGroup>
          {requiredCards.map((card) => (
            <SectionCard key={card.section} summary={card} onOpen={setEditing} />
          ))}
        </SectionCardGroup>

        <div className="mx-1 mb-1.5 mt-3 flex items-baseline justify-between">
          <h2 className="text-sm font-bold">선택 정보</h2>
          <span className="text-[11px] text-ink-muted">입력하지 않아도 제출할 수 있어요</span>
        </div>
        <SectionCardGroup>
          {optionalCards.map((card) => (
            <SectionCard key={card.section} summary={card} onOpen={setEditing} />
          ))}
        </SectionCardGroup>

        <div className="mx-0.5 mt-2 flex flex-nowrap items-center gap-x-2 whitespace-nowrap text-[10px] tracking-tight text-ink-sub">
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

        {coverLetter && <CoverLetterCard applicationId={applicationId} section={coverLetter} />}
      </div>

      <footer className="fixed bottom-0 left-1/2 z-20 flex w-[390px] -translate-x-1/2 flex-col gap-1 border-t border-line bg-surface px-5 pb-3 pt-2.5">
        <button
          type="button"
          onClick={() => setConfirmingSubmit(true)}
          className="flex min-h-12 w-full items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          담당자에게 검토 요청
        </button>
        <p className="text-center text-[11px] leading-4 text-ink-muted">
          검토 후 기관 추천으로 전달돼요 · 기업에 바로 가지 않아요
        </p>
      </footer>

      <BottomSheet
        open={editing !== null}
        title={editing?.label ?? ""}
        description={
          editing?.readOnly
            ? "수료한 교육 과정 정보라 여기서 고칠 수 없어요."
            : "내용을 확인하고 필요하면 고쳐 주세요."
        }
        onClose={() => setEditing(null)}
        actions={
          <button type="button" onClick={() => setEditing(null)} className={secondaryButton}>
            닫기
          </button>
        }
      >
        {/* TODO(FE-B-05): 섹션별 입력칸 + "내 프로필에도 저장" 체크박스 */}
        <p className="mt-4 rounded-xl border border-line px-3 py-3 text-sm">{editing?.text}</p>
      </BottomSheet>

      <BottomSheet
        open={confirmingSubmit}
        title="담당자에게 서류를 보낼까요?"
        description={`${job.companyName} · ${job.title} 지원 서류가 담당 코디네이터 검토 단계로 넘어가요. 기업에 바로 보내지는 않아요.`}
        onClose={() => setConfirmingSubmit(false)}
        actions={
          <>
            <button type="button" onClick={() => setConfirmingSubmit(false)} className={secondaryButton}>
              취소
            </button>
            {/* TODO(FE-B-03): submitApplication 호출 후 M7(/student/applications)로 이동 */}
            <button type="button" onClick={() => setConfirmingSubmit(false)} className={primaryButton}>
              검토 요청
            </button>
          </>
        }
      />
    </div>
  );
}

function CoverLetterCard({ applicationId, section }: { applicationId: number; section: CoverLetterSection }) {
  const [first, ...rest] = section.sentences;
  const aiCount = section.sentences.filter((s) => s.source === "AI").length;
  const userCount = section.sentences.length - aiCount;

  return (
    <>
      {/* 카드 안에 "자기소개서" 제목이 있어 바깥 제목은 두지 않는다 (한 화면에 맞추기) */}
      <Link
        href={`/student/applications/${applicationId}/cover-letter`}
        className="mt-2 flex flex-col gap-1.5 rounded-2xl border-[1.5px] border-primary bg-surface px-4 py-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <span className="flex flex-wrap items-center gap-1.5">
          <b className="mr-auto text-sm">자기소개서</b>
          {aiCount > 0 && <SourceTag source="AI" label={`AI 작성 ${aiCount}문장 · 근거 연결`} />}
          {userCount > 0 && <SourceTag source="USER" label={`직접 입력 ${userCount}문장`} />}
        </span>
        {first ? (
          <span className="line-clamp-2 text-[13px] leading-[1.6] text-ink-sub">
            <span className={first.source === "AI" ? "border-b-2 border-primary bg-primary-tint" : undefined}>
              {first.text}
            </span>
            {rest.length > 0 && " …"}
          </span>
        ) : (
          <span className="text-[13px] text-ink-muted">아직 작성된 문장이 없어요</span>
        )}
        <span className="text-xs font-bold text-primary">전체 보기 · 근거 확인 · 수정 ›</span>
      </Link>
    </>
  );
}
