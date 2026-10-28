"use client";
// 주인: FE-B — C4 서류 검토 · 승인 (FE-B-08). 서류 전체 + 선택 문장 근거 + 검증 요약 + 반려 · 승인 · 전달 완료
// API: docs/API.md 2-3. 승인 SUBMITTED → APPROVED, 반려 { note } → REJECTED, 전달 완료 APPROVED → DELIVERED.
// 상태가 맞지 않으면 서버가 409 → 최신 상태를 다시 불러온다. 왼쪽 서류는 C4 전용 요약(C4DocumentSummary), 근거는 FE-B-01 EvidencePanel.
import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import C4DocumentSummary, { coverLetterMarkers, userEditBreakdown } from "@/components/coordinator/C4DocumentSummary";
import { notifyReviewQueueChanged } from "@/components/coordinator/CoordinatorSidebar";
import EvidencePanel from "@/components/resume/EvidencePanel";
import { applicationLabels } from "@/components/student/labels";
import StatusChip from "@/components/ui/StatusChip";
import { ApiError } from "@/lib/api/client";
import {
  approveApplication,
  deliverApplication,
  getCoordinatorApplication,
  rejectApplication,
} from "@/lib/api/coordinatorReview";
import type { CoordinatorApplication } from "@/types/coordinator";
import type { Sentence } from "@/types/resume";
import type { ApplicationStatus, CompanyInfoStatus } from "@/types/student";

type Action = "APPROVE" | "REJECT" | "DELIVER";

const ACTION: Record<Action, { title: string; description: string; confirm: string; done: string }> = {
  APPROVE: {
    title: "이 서류를 승인할까요?",
    description: "승인하면 기관 추천으로 기업에 전달할 준비가 돼요. 전달은 \"전달 완료로 변경\"으로 따로 기록해요.",
    confirm: "승인",
    done: "승인했어요. 기업에 전달한 뒤 \"전달 완료로 변경\"을 눌러 주세요.",
  },
  REJECT: {
    title: "이 서류를 반려할까요?",
    description: "반려하면 수료생에게 직접 연락해 보완할 점을 알려 주세요. 아래 메모는 기록으로 남아요.",
    confirm: "반려",
    done: "반려했어요. 수료생에게 연락해 보완할 점을 알려 주세요.",
  },
  DELIVER: {
    title: "전달 완료로 바꿀까요?",
    description: "기관 추천으로 기업에 전달했을 때 눌러 주세요. 이음에서 실제로 보내지는 않아요.",
    confirm: "전달 완료",
    done: "전달 완료로 바꿨어요. 수료생 지원 현황에도 반영돼요.",
  },
};

/** 버튼이 켜지는 상태 (docs/API.md 2-3 상태 전이) */
const ALLOWED: Record<Action, ApplicationStatus> = { APPROVE: "SUBMITTED", REJECT: "SUBMITTED", DELIVER: "APPROVED" };

/** 코디네이터 입장 상태 이름: 제출된 서류는 "검토 대기", 나머지는 기존 지원서 상태 라벨(FE-A labels) 그대로 */
const STATUS_LABEL: Record<ApplicationStatus, string> = { ...applicationLabels, SUBMITTED: "검토 대기" };
const STATUS_TONE: Record<ApplicationStatus, "neutral" | "success" | "warning" | "danger"> = {
  GENERATING: "neutral",
  DRAFT: "neutral",
  SUBMITTED: "warning",
  APPROVED: "success",
  DELIVERED: "success",
  REJECTED: "danger",
  FAILED: "danger",
};

const COMPANY_INFO_LABEL: Record<CompanyInfoStatus, string> = {
  FOUND: "기업 정보 확인",
  PARTIAL: "기업 정보 일부만 확인",
  NOT_FOUND: "공고 외 정보 확인 불가",
};

export default function ApplicationReviewScreen({ applicationId }: { applicationId: number }) {
  const [detail, setDetail] = useState<CoordinatorApplication | null>(null);
  // notFound(404)면 다시 불러와도 같으므로 "다시 시도"를 숨긴다
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<Sentence | null>(null);
  const [confirming, setConfirming] = useState<Action | null>(null);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);
  // 화면이 다시 그려지기 전 두 번 눌러도 요청이 한 번만 가도록
  const busyRef = useRef(false);

  useEffect(() => {
    let ignore = false;
    getCoordinatorApplication(applicationId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        setLoadError(
          e instanceof ApiError
            ? { message: e.message, notFound: e.status === 404 }
            : { message: "서류를 불러오지 못했어요.", notFound: false },
        );
      });
    return () => {
      ignore = true;
    };
  }, [applicationId, reloadKey]);

  if (!detail) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-3 text-sm text-ink-sub">
        <p>{loadError?.message ?? "서류를 불러오는 중이에요…"}</p>
        {loadError?.notFound && <p className="text-xs text-ink-muted">주소의 지원서 번호를 확인하거나 대시보드에서 다시 골라 주세요.</p>}
        {loadError && (
          <div className="flex gap-4">
            {!loadError.notFound && (
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
            <Link href="/coordinator" className={`font-bold ${loadError.notFound ? "text-primary" : "text-ink-sub"}`}>
              대시보드로
            </Link>
          </div>
        )}
      </div>
    );
  }

  const { status, student, job, document, verification } = detail;

  const closeConfirm = () => {
    if (busy) return;
    setConfirming(null);
    setActionError(null);
  };

  const run = async (action: Action) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setActionError(null);
    try {
      const result =
        action === "APPROVE"
          ? await approveApplication(applicationId)
          : action === "REJECT"
            ? await rejectApplication(applicationId, { note: note.trim() })
            : await deliverApplication(applicationId);
      setDetail((prev) => (prev ? { ...prev, status: result.status } : prev));
      // 사이드바가 대시보드 API 로 대기 건수를 다시 센다
      notifyReviewQueueChanged();
      setDoneMessage(ACTION[action].done);
      setConfirming(null);
      setNote("");
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setActionError(e instanceof ApiError ? e.message : "처리하지 못했어요. 다시 시도해 주세요.");
      // 그사이 다른 처리로 상태가 바뀌었으면(409) 최신 상태로 버튼을 맞춘다
      if (e instanceof ApiError && e.status === 409) {
        setReloadKey((k) => k + 1);
        notifyReviewQueueChanged();
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const hasAi = verification.aiSentenceCount > 0;
  const allAiLinked = verification.aiSentenceWithEvidence === verification.aiSentenceCount;
  // AI 문장이 0개면 "모두 근거 연결"이 아니라 확인할 문장이 없다고 알린다
  const aiValue = !hasAi
    ? "없음"
    : `${verification.aiSentenceCount}개 · ${allAiLinked ? "모두 근거 연결" : `근거 연결 ${verification.aiSentenceWithEvidence}개`}`;
  const markers = coverLetterMarkers(document);
  // 종류별 합이 서버의 userEditedCount 와 같을 때만 나눠 보여 준다 (다르면 서버 총합만)
  const edits = userEditBreakdown(document);
  const editsTotal = edits.reduce((sum, e) => sum + e.count, 0);
  const editedValue =
    edits.length > 0 && editsTotal === verification.userEditedCount
      ? edits.map((e) => `${e.label} ${e.count}`).join(" · ")
      : `${verification.userEditedCount}개`;

  return (
    <div className="flex flex-col gap-5">
      <header className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <p className="flex items-center gap-1.5 text-xs text-ink-sub">
            <Link href="/coordinator" className="font-bold hover:text-ink">
              ← 대시보드
            </Link>
            <span aria-hidden="true">·</span>
            {/* C4 API 에 제출 시각이 없어서 날짜는 표시하지 않는다 */}
            <span>서류 검토</span>
          </p>
          <h1 className="text-[22px] font-bold">
            <Link href={`/coordinator/students/${student.studentId}`} className="underline-offset-4 hover:underline">
              {student.name}
            </Link>{" "}
            → {job.companyName} {job.title}
          </h1>
        </div>
        {/* 목업처럼 제목 오른쪽 위 상태 배지 (실제 status 기준) */}
        <StatusChip tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</StatusChip>
      </header>

      {/* 왼쪽 서류는 페이지와 함께 세로로 스크롤, 오른쪽 패널은 화면에 붙어 근거 · 버튼이 계속 보이게 */}
      <div className="grid grid-cols-[minmax(0,1fr)_340px] items-start gap-6">
        <C4DocumentSummary
          document={document}
          selectedSentenceId={selected?.sentenceId ?? null}
          onSelectSentence={setSelected}
        />

        <aside className="sticky top-6 flex max-h-[calc(100vh-48px)] flex-col gap-3 overflow-y-auto">
          <section className="flex flex-col gap-2 rounded-2xl border border-line bg-surface p-4">
            <h2 className="text-[15px] font-bold">선택한 문장의 근거</h2>
            {selected && <p className="rounded-lg bg-coord-bg px-3 py-2 text-[13px] leading-relaxed">{selected.text}</p>}
            <EvidencePanel sentence={selected} bare marker={selected ? markers.get(selected.sentenceId) : undefined} />
          </section>

          <section className="flex flex-col gap-2 rounded-2xl border border-line bg-surface p-4">
            <h2 className="text-[15px] font-bold">검증 요약</h2>
            <dl className="flex flex-col gap-1.5 text-[13px]">
              <SummaryRow
                label="AI 문장"
                value={aiValue}
                note={hasAi ? null : "AI가 쓴 문장이 없어 확인할 근거가 없어요."}
                warn={hasAi && !allAiLinked}
              />
              <SummaryRow label="생성 단계에서 제외된 문장" value={`${verification.excludedSentenceCount}개`} />
              <SummaryRow label="수료생 직접 수정" value={editedValue} warn={verification.userEditedCount > 0} />
              <SummaryRow
                label="기업 정보"
                value={COMPANY_INFO_LABEL[verification.companyInfoStatus]}
                note={verification.companyInfoStatus !== "FOUND" ? document.companyInfoNote : null}
                warn={verification.companyInfoStatus === "NOT_FOUND"}
              />
            </dl>
          </section>

          <section className="flex flex-col gap-2.5 rounded-2xl border border-line bg-surface p-4">
            {/* 처리 결과는 버튼 바로 위에 (왼쪽 서류 높이가 밀리지 않게) */}
            {doneMessage ? (
              <p role="status" className="flex flex-col gap-1 rounded-xl bg-primary-tint px-3 py-2.5 text-[13px] font-bold text-primary-strong">
                {doneMessage}
                <Link href="/coordinator" className="w-fit text-xs underline underline-offset-2">
                  대시보드로 돌아가기
                </Link>
              </p>
            ) : (
              <p className="text-xs leading-relaxed text-ink-sub">
                승인하면 기관 추천으로 기업에 전달할 준비가 돼요. 반려하면 수료생에게 직접 연락해 주세요.
              </p>
            )}
            <button
              type="button"
              disabled={status !== ALLOWED.APPROVE}
              onClick={() => setConfirming("APPROVE")}
              className="min-h-10 rounded-xl bg-primary text-sm font-bold text-surface disabled:bg-line disabled:text-ink-muted"
            >
              승인 · 기관 추천으로 전달
            </button>
            <button
              type="button"
              disabled={status !== ALLOWED.REJECT}
              onClick={() => setConfirming("REJECT")}
              className="min-h-10 rounded-xl border-[1.5px] border-danger bg-surface text-sm font-bold text-danger disabled:border-line disabled:text-ink-muted"
            >
              반려 · 수료생에게 연락
            </button>
            <button
              type="button"
              disabled={status !== ALLOWED.DELIVER}
              onClick={() => setConfirming("DELIVER")}
              className="min-h-10 rounded-xl border-[1.5px] border-primary bg-surface text-sm font-bold text-primary disabled:border-line disabled:text-ink-muted"
            >
              전달 완료로 변경 (승인 후)
            </button>
          </section>
        </aside>
      </div>

      {confirming && (
        <ConfirmDialog
          title={ACTION[confirming].title}
          description={ACTION[confirming].description}
          confirmLabel={busy ? "처리 중…" : ACTION[confirming].confirm}
          danger={confirming === "REJECT"}
          busy={busy}
          confirmDisabled={confirming === "REJECT" && note.trim() === ""}
          error={actionError}
          onConfirm={() => run(confirming)}
          onClose={closeConfirm}
        >
          {confirming === "REJECT" && (
            <label className="flex flex-col gap-1 text-xs text-ink-sub">
              보완할 점 (필수)
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={4}
                placeholder="예: 자기소개서 2문단의 수치 근거를 README와 맞춰 주세요"
                className="resize-none rounded-[10px] border-[1.5px] border-line p-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-primary"
              />
            </label>
          )}
        </ConfirmDialog>
      )}
    </div>
  );
}

function SummaryRow({
  label,
  value,
  note = null,
  warn = false,
}: {
  label: string;
  value: string;
  note?: string | null;
  warn?: boolean;
}) {
  return (
    <div className="flex flex-col gap-0.5 border-b border-line pb-1.5 last:border-b-0 last:pb-0">
      <div className="flex justify-between gap-2">
        <dt className="text-ink-sub">{label}</dt>
        <dd className={`text-right font-bold ${warn ? "text-warn-ink" : "text-ink"}`}>{value}</dd>
      </div>
      {note && <dd className="text-xs text-ink-muted">{note}</dd>}
    </div>
  );
}

/** 가운데 확인 창 (데스크톱). 배경 · Esc 로 닫힌다 */
function ConfirmDialog({
  title,
  description,
  confirmLabel,
  danger,
  busy,
  confirmDisabled,
  error,
  onConfirm,
  onClose,
  children,
}: {
  title: string;
  description: string;
  confirmLabel: string;
  danger: boolean;
  busy: boolean;
  confirmDisabled: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
  children?: React.ReactNode;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  // 처음 열릴 때만 창으로 포커스 (입력 중에 다시 옮기지 않도록 onClose 와 분리)
  useEffect(() => {
    panelRef.current?.focus();
  }, []);
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-30 flex items-center justify-center bg-ink/50" onClick={onClose}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
        className="flex w-[440px] flex-col gap-3 rounded-2xl bg-surface p-6 outline-none"
      >
        <h2 id={titleId} className="text-lg font-bold">
          {title}
        </h2>
        <p className="text-[13px] leading-relaxed text-ink-sub">{description}</p>
        {children}
        {error && (
          <p role="alert" className="text-[13px] text-danger">
            {error}
          </p>
        )}
        <div className="mt-2 flex gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="min-h-11 flex-1 rounded-xl border border-line bg-surface text-sm font-bold text-ink"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy || confirmDisabled}
            aria-busy={busy}
            className={`min-h-11 flex-1 rounded-xl text-sm font-bold text-surface disabled:bg-line disabled:text-ink-muted ${danger ? "bg-danger" : "bg-primary"}`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
