"use client";
// 주인: FE-B — M8 내 프로필 화면 (FE-B-06). 역량 프로필 카드 + 등록 자료 목록 + 섹션 편집 시트 + "반영 완료" 배너
// 편집 시트는 M6-2(FE-B-05) 부품을 그대로 쓰고, 저장만 프로필 API(docs/API.md 2-2)로 보낸다.
//   - 추가 POST /profile/items · 수정 PATCH /profile/items/{itemId} · 삭제 DELETE /profile/items/{itemId}?section=
//   - 기본정보 · 병역 · 기술은 itemId 자리에 studentId (BE-B ProfileItemController)
//   - 포트폴리오는 프로젝트 itemId 로 { include } · 자기소개서 원본은 PUT /cover-letter { content }
//   - "내 프로필에도 저장" 체크박스는 숨긴다 (항상 프로필에 저장)
import { useEffect, useState } from "react";
import BottomSheet from "@/components/resume/BottomSheet";
import { SingleForm, TagEditor, type Runner, type Save } from "@/components/resume/SectionDetailSheet";
import SectionEditSheet from "@/components/resume/SectionEditSheet";
import { SectionCard, SectionCardGroup } from "@/components/resume/SectionCard";
import SourceTag from "@/components/resume/SourceTag";
import {
  MILITARY_FIELDS,
  PROFILE_BASIC_FIELDS,
  PROFILE_PROJECT_CONFIG,
  SECTION_FIELDS,
  type ProfileProjectItem,
} from "@/components/resume/sectionFields";
import type { SectionSummary } from "@/components/resume/sectionSummary";
import {
  DoneButton,
  ErrorText,
  applyButton,
  cancelButton,
  inputClass,
  useSaveRunner,
  type ItemChange,
} from "@/components/resume/sheetParts";
import { ApiError } from "@/lib/api/client";
import { addProfileItem, deleteProfileItem, getProfile, updateCoverLetter, updateProfileItem } from "@/lib/api/profile";
import type { StudentProfile } from "@/types/profile";
import type { SectionName } from "@/types/resume";
import type { Source } from "@/types/student";

const DESCRIPTION = "적용을 누르면 바로 저장되고, 다음 추천부터 반영돼요.";

/** "10.24" */
const shortDate = (iso: string) => `${iso.slice(5, 7)}.${iso.slice(8, 10)}`;

/** 직접 고친 항목이 하나라도 있으면 "직접 입력", 아니면 등록 */
const pickSource = (sources: Source[]): Source | null =>
  sources.length === 0 ? null : sources.includes("USER") ? "USER" : "REGISTERED";

export default function ProfileScreen({ studentId }: { studentId: number }) {
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<SectionSummary | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    getProfile(studentId)
      .then((data) => {
        if (!ignore) setProfile(data);
      })
      .catch((e: unknown) => {
        if (!ignore) setLoadError(e instanceof ApiError ? e.message : "프로필을 불러오지 못했어요.");
      });
    return () => {
      ignore = true;
    };
  }, [studentId, reloadKey]);

  if (!profile) {
    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 px-4 text-sm text-ink-sub">
        <p>{loadError ?? "프로필을 불러오는 중이에요…"}</p>
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
      </div>
    );
  }

  /** 저장 성공 → 응답의 프로필 전체로 화면 갱신 + 배너. 실패는 throw → 시트가 입력값을 유지 */
  const applyResult = (result: StudentProfile, label: string) => {
    // 역량 프로필 시각이 바뀌었을 때만 "반영"이라고 쓴다
    const refreshed = result.profileUpdatedAt !== null && result.profileUpdatedAt !== profile.profileUpdatedAt;
    setProfile(result);
    setBanner(
      refreshed && result.profileUpdatedAt
        ? `${label} 저장 완료 · 역량 프로필에 반영했어요 (${shortDate(result.profileUpdatedAt)})`
        : `${label} 저장 완료`,
    );
  };

  const saveItem = async (section: SectionName, label: string, change: ItemChange) => {
    let result: StudentProfile;
    if (change.op === "ADD_ITEM") {
      result = await addProfileItem(studentId, { section, item: change.item });
    } else if (change.op === "UPDATE_ITEM") {
      const itemId = change.itemId === null ? studentId : Number(change.itemId);
      result = await updateProfileItem(studentId, itemId, { section, item: change.item });
    } else if (change.op === "DELETE_ITEM") {
      result = await deleteProfileItem(studentId, Number(change.itemId), section);
    } else {
      // 문장 수정(UPDATE_SENTENCE)은 지원 서류에만 있다
      throw new Error("프로필에서는 고칠 수 없는 항목이에요.");
    }
    applyResult(result, label);
  };

  const saveCoverLetter = async (content: string) => {
    applyResult(await updateCoverLetter(studentId, { content }), "자기소개서");
  };

  const { basic, course } = profile;
  const militaryLabel = MILITARY_FIELDS[0].options?.find((o) => o.value === basic.militaryStatus)?.label ?? null;
  const portfolioFiles = profile.projects.filter((p) => p.portfolioFileName);
  const row = (
    section: SectionName,
    label: string,
    text: string | null,
    emptyText: string,
    source: Source | null = null,
  ): SectionSummary => ({
    section,
    label,
    text: text || emptyText,
    isEmpty: !text,
    source: text ? source : null,
    readOnly: section === "TRAINING",
  });
  const rows: SectionSummary[] = [
    row(
      "BASIC",
      "기본정보",
      [basic.name, basic.desiredJob && `희망 직무 ${basic.desiredJob}`].filter(Boolean).join(" · "),
      "추가하려면 누르세요",
    ),
    row("MILITARY", "병역", militaryLabel, "선택 입력 · 비워 둬도 돼요"),
    row(
      "EDUCATION",
      "학력",
      profile.education.map((e) => e.schoolName).join(", "),
      "추가하려면 누르세요",
      pickSource(profile.education.map((e) => e.source)),
    ),
    row("TRAINING", "교육", `${course.name} 수료`, "", "REGISTERED"),
    row(
      "CAREER",
      "경력",
      profile.careers.map((c) => [c.companyName, c.position].filter(Boolean).join(" · ")).join(", "),
      "신입이라면 비워 두세요",
      pickSource(profile.careers.map((c) => c.source)),
    ),
    row(
      "CERTIFICATES",
      "자격증",
      profile.certificates.map((c) => c.name).join(", "),
      "추가하려면 누르세요",
      pickSource(profile.certificates.map((c) => c.source)),
    ),
    row("SKILLS", "기술", profile.skills.join(" · "), "추가하려면 누르세요"),
    row("PROJECTS", "프로젝트", profile.projects.map((p) => p.title).join(", "), "추가하려면 누르세요"),
    row(
      "PORTFOLIO",
      "포트폴리오",
      portfolioFiles.length > 0
        ? `파일 ${portfolioFiles.length}개 · 지원서 첨부 ${portfolioFiles.filter((p) => p.includeInApply).length}개`
        : null,
      "등록한 파일이 없어요",
      // 포트폴리오 파일은 기관에 등록된 자료
      "REGISTERED",
    ),
    row("COVER_LETTER", "자기소개서", profile.coverLetter ? "원본 · 공고별 초안의 바탕" : null, "추가하려면 누르세요"),
  ];

  const close = () => setEditing(null);
  const section = editing?.section;
  const onItemChange = (change: ItemChange) =>
    editing ? saveItem(editing.section, editing.label, change) : Promise.resolve();
  const projectItems: ProfileProjectItem[] = profile.projects.map((p) => ({
    itemId: String(p.itemId),
    title: p.title,
    periodText: p.periodText,
    teamSize: p.teamSize,
    // BE-B 는 techStack 을 null 로 비울 수 있다
    techStack: p.techStack ?? [],
  }));

  return (
    <div className="flex flex-col gap-4 px-4 pb-6 pt-5">
      <header className="mx-1 flex flex-col gap-1">
        <h1 className="text-xl font-bold">내 프로필</h1>
        <p className="text-xs text-ink-sub">수료 때 등록한 자료예요. 고치면 다음 추천부터 바로 반영돼요.</p>
      </header>

      {banner && (
        <p
          role="status"
          className="flex items-center gap-2 rounded-xl bg-primary-tint px-3 py-2.5 text-xs font-bold text-primary-strong"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0">
            <path d="M20 6 9 17l-5-5" />
          </svg>
          {banner}
        </p>
      )}

      <CompetencyCard profile={profile} />

      <div className="flex flex-col gap-2">
        <h2 className="mx-1 text-sm font-bold">등록 자료</h2>
        <SectionCardGroup>
          {rows.map((r) => (
            <SectionCard key={r.section} summary={r} onOpen={setEditing} />
          ))}
        </SectionCardGroup>
      </div>

      {(section === "EDUCATION" || section === "CAREER" || section === "CERTIFICATES") && (
        <SectionEditSheet
          config={SECTION_FIELDS[section]}
          items={(section === "EDUCATION"
            ? profile.education
            : section === "CAREER"
              ? profile.careers
              : profile.certificates
          ).map((i) => ({ ...i, itemId: String(i.itemId) }))}
          editable
          showSaveToProfile={false}
          onChange={onItemChange}
          onClose={close}
        />
      )}
      {section === "PROJECTS" && (
        <SectionEditSheet
          config={PROFILE_PROJECT_CONFIG}
          items={projectItems}
          editable
          showSaveToProfile={false}
          onChange={onItemChange}
          onClose={close}
        />
      )}
      {section === "BASIC" && (
        <FormSheet title="기본정보" onChange={onItemChange} onClose={close}>
          {(runner, save) => (
            <SingleForm fields={PROFILE_BASIC_FIELDS} data={basic} editable target="basic" runner={runner} save={save} />
          )}
        </FormSheet>
      )}
      {section === "MILITARY" && (
        <FormSheet title="병역" onChange={onItemChange} onClose={close}>
          {(runner, save) => (
            <SingleForm
              fields={MILITARY_FIELDS}
              data={{ value: basic.militaryStatus }}
              editable
              target="military"
              runner={runner}
              save={save}
            />
          )}
        </FormSheet>
      )}
      {section === "SKILLS" && (
        <FormSheet title="기술" onChange={onItemChange} onClose={close}>
          {(runner, save) => <TagEditor tags={profile.skills} editable runner={runner} save={save} />}
        </FormSheet>
      )}
      {section === "PORTFOLIO" && (
        <FormSheet
          title="포트폴리오"
          description="지원서에 첨부할 파일을 고르면 바로 저장돼요."
          onChange={onItemChange}
          onClose={close}
        >
          {(runner, save) => <PortfolioList projects={profile.projects} runner={runner} save={save} />}
        </FormSheet>
      )}
      {section === "COVER_LETTER" && (
        <CoverLetterSheet content={profile.coverLetter?.content ?? ""} onSave={saveCoverLetter} onClose={close} />
      )}
      {section === "TRAINING" && (
        <BottomSheet
          open
          title="교육"
          description="수료한 교육 과정 정보라 여기서 고칠 수 없어요."
          onClose={close}
          actions={<DoneButton onClick={close} disabled={false} />}
        >
          <p className="mt-4 flex flex-col gap-0.5 rounded-xl border border-line px-3 py-3 text-sm">
            <span>
              {course.name} · {course.cohort} · {course.campus}
            </span>
            <span className="text-xs text-ink-sub">
              {course.startedOn} ~ {course.endedOn}
            </span>
          </p>
        </BottomSheet>
      )}
    </div>
  );
}

/** 추천에 쓰이는 역량 프로필 (AI 정리). 등록 자료를 고치면 BE-B 가 다시 만든다 */
function CompetencyCard({ profile }: { profile: StudentProfile }) {
  const cp = profile.competencyProfile;
  const tags = cp ? [...new Set([...cp.skills.map((s) => s.name), ...cp.certificates])] : [];
  return (
    <section className="flex flex-col gap-2 rounded-2xl border-[1.5px] border-primary bg-surface px-4 py-3.5">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-bold">추천에 쓰이는 역량 프로필</h2>
        <SourceTag source="AI" label="AI 정리" />
      </div>
      {cp ? (
        <>
          {cp.roles.length > 0 && <p className="text-[13px] text-ink">{cp.roles.join(", ")}</p>}
          {tags.length > 0 && (
            <ul className="flex flex-wrap gap-1.5" aria-label="역량 기술 태그">
              {tags.map((t) => (
                <li key={t} className="rounded-md bg-page px-2 py-0.5 text-xs text-ink-sub">
                  {t}
                </li>
              ))}
            </ul>
          )}
          <p className="text-[11px] text-ink-muted">아래 자료를 고치면 자동으로 다시 정리돼요</p>
        </>
      ) : (
        <p className="text-[13px] text-ink-muted">아직 정리된 역량 프로필이 없어요. 아래 자료를 채우면 정리돼요.</p>
      )}
    </section>
  );
}

/** 시트 틀 + 저장 실행기. children 에 runner · save 를 넘겨 M6-2 입력 부품을 그대로 쓴다 */
function FormSheet({
  title,
  description = DESCRIPTION,
  onChange,
  onClose,
  children,
}: {
  title: string;
  description?: string;
  onChange: (change: ItemChange) => Promise<void>;
  onClose: () => void;
  children: (runner: Runner, save: Save) => React.ReactNode;
}) {
  const runner = useSaveRunner();
  const save: Save = (target, change, onSuccess) => runner.run(target, () => onChange(change), onSuccess);
  return (
    <BottomSheet
      open
      title={title}
      description={description}
      onClose={() => {
        if (!runner.saving) onClose();
      }}
      actions={<DoneButton onClick={onClose} disabled={runner.saving} />}
    >
      <div className="mt-4 flex flex-col gap-3">{children(runner, save)}</div>
    </BottomSheet>
  );
}

/** 프로젝트별 포트폴리오 파일 · 지원서 첨부 여부. 파일 올리기는 P0 범위 밖 (BE-B: 파일명만 저장) */
function PortfolioList({ projects, runner, save }: { projects: StudentProfile["projects"]; runner: Runner; save: Save }) {
  const withFile = projects.filter((p) => p.portfolioFileName);
  if (withFile.length === 0) return <p className="text-[13px] text-ink-muted">등록한 파일이 없어요</p>;
  return (
    <ul className="flex flex-col gap-2">
      {withFile.map((p) => {
        const target = String(p.itemId);
        return (
          <li key={p.itemId} className="flex flex-col gap-1.5 rounded-[10px] border-[1.5px] border-line px-3 py-2.5">
            <label className="flex items-center gap-2 text-sm text-ink">
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="break-all">{p.portfolioFileName}</span>
                <small className="text-xs text-ink-sub">{p.title}</small>
              </span>
              <span className="shrink-0 text-xs text-ink-sub">{p.includeInApply ? "지원서 첨부" : "첨부 안 함"}</span>
              <input
                type="checkbox"
                checked={p.includeInApply}
                disabled={runner.saving}
                onChange={(e) =>
                  save(target, { op: "UPDATE_ITEM", itemId: target, item: { include: e.target.checked } })
                }
                aria-label={`${p.portfolioFileName} 지원서 첨부`}
                className="size-5 shrink-0 accent-primary"
              />
            </label>
            <ErrorText error={runner.error} target={target} />
          </li>
        );
      })}
    </ul>
  );
}

/** 자기소개서 원본 (PUT /cover-letter { content }). 공고별 초안의 바탕이 된다 */
function CoverLetterSheet({
  content: saved,
  onSave,
  onClose,
}: {
  content: string;
  onSave: (content: string) => Promise<void>;
  onClose: () => void;
}) {
  const runner = useSaveRunner();
  const [content, setContent] = useState(saved);
  const changed = content.trim() !== saved.trim();
  return (
    <BottomSheet
      open
      title="자기소개서"
      description="공고별 자기소개서 초안의 바탕이 되는 원본이에요."
      onClose={() => {
        if (!runner.saving) onClose();
      }}
      actions={<DoneButton onClick={onClose} disabled={runner.saving} />}
    >
      <div className="mt-4 flex flex-col gap-2">
        <label className="flex flex-col gap-1 text-xs text-ink-sub">
          자기소개서 원본
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={10}
            className={`${inputClass} resize-none py-2.5 leading-relaxed`}
          />
        </label>
        <ErrorText error={runner.error} target="cover-letter" />
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              setContent(saved);
              runner.clearError();
            }}
            disabled={runner.saving || !changed}
            className={cancelButton}
          >
            되돌리기
          </button>
          <button
            type="button"
            onClick={() => runner.run("cover-letter", () => onSave(content.trim()))}
            disabled={runner.saving || !changed || content.trim() === ""}
            aria-busy={runner.saving}
            className={applyButton}
          >
            {runner.saving ? "저장 중…" : "적용"}
          </button>
        </div>
      </div>
    </BottomSheet>
  );
}
