"use client";
// 주인: FE-B — M6-2 섹션 편집 바텀시트 (FE-B-05). 목록 섹션이 아닌 기본정보 · 병역 · 기술 · 경험 · 포트폴리오
// 저장 요청은 docs/API.md 2-1 (BE-A 확정). UPDATE_ITEM 의 item 에는 바뀐 필드만 보낸다.
//   - 기본정보 · 병역 · 기술 태그: itemId null. 병역은 { value }, 태그는 { tags: 전체 목록 }
//   - 프로젝트 정보: 프로젝트 itemId, title · periodText · teamSize · role 중 바뀐 것만 (bullets 제외)
//   - 기술 요약 문장 · 프로젝트 문장: UPDATE_SENTENCE
//   - 포트폴리오: { include } 만. 파일 추가 · 교체는 M8
import { useState } from "react";
import BottomSheet from "@/components/resume/BottomSheet";
import SourceTag from "@/components/resume/SourceTag";
import {
  BASIC_FIELDS,
  MILITARY_FIELDS,
  PROJECT_FIELDS,
  changedItemInput,
  hasChanges,
  isFormValid,
  toFormValues,
  type FieldDef,
  type FormValues,
} from "@/components/resume/sectionFields";
import {
  DoneButton,
  ErrorText,
  Fields,
  IconButton,
  PencilIcon,
  SaveToProfileCheckbox,
  TagInput,
  applyButton,
  cancelButton,
  inputClass,
  useSaveRunner,
  type ItemChange,
} from "@/components/resume/sheetParts";
import type {
  BasicSection,
  MilitarySection,
  PortfolioSection,
  ProjectsSection,
  Sentence,
  SkillsSection,
} from "@/types/resume";

export type DetailSection = BasicSection | MilitarySection | SkillsSection | ProjectsSection | PortfolioSection;

interface SectionDetailSheetProps {
  section: DetailSection;
  /** false 면 내용만 보여 준다 (예: 이미 검토를 요청한 서류) */
  editable: boolean;
  /** "내 프로필에도 저장" 체크박스. M6 은 표시(기본 선택), M8 은 숨김. 문장 수정(UPDATE_SENTENCE)에는 쓰이지 않는다 */
  showSaveToProfile?: boolean;
  /** 저장 한 건. 실패하면 throw → 시트가 입력값을 유지하고 오류를 보여 준다 */
  onChange: (change: ItemChange, saveToProfile: boolean) => Promise<void>;
  onClose: () => void;
}

export type Save = (target: string, change: ItemChange, onSuccess?: () => void) => Promise<void>;
export type Runner = ReturnType<typeof useSaveRunner>;

const TITLE: Record<DetailSection["section"], string> = {
  BASIC: "기본정보",
  MILITARY: "병역",
  SKILLS: "기술",
  PROJECTS: "경험",
  PORTFOLIO: "포트폴리오",
};

const DESCRIPTION: Record<DetailSection["section"], string> = {
  BASIC: "적용을 누르면 바로 저장돼요.",
  MILITARY: "선택 입력이에요. 적용을 누르면 바로 저장돼요.",
  SKILLS: "문장 · 태그는 적용을 누르면 바로 저장돼요.",
  PROJECTS: "프로젝트 정보 · 문장은 적용을 누르면 바로 저장돼요. 문장은 이 서류에만 반영돼요.",
  PORTFOLIO: "지원서에 첨부할 파일을 고르면 바로 저장돼요.",
};

export default function SectionDetailSheet({
  section,
  editable,
  showSaveToProfile = true,
  onChange,
  onClose,
}: SectionDetailSheetProps) {
  const runner = useSaveRunner();
  const [saveToProfile, setSaveToProfile] = useState(true);
  const save: Save = (target, change, onSuccess) =>
    runner.run(target, () => onChange(change, showSaveToProfile ? saveToProfile : false), onSuccess);

  return (
    <BottomSheet
      open
      title={TITLE[section.section]}
      description={editable ? DESCRIPTION[section.section] : "검토를 요청한 서류라 지금은 내용만 확인할 수 있어요."}
      onClose={() => {
        if (!runner.saving) onClose();
      }}
      actions={<DoneButton onClick={onClose} disabled={runner.saving} />}
    >
      <div className="mt-4 flex flex-col gap-3">
        {section.section === "BASIC" && (
          <SingleForm fields={BASIC_FIELDS} data={section.fields} editable={editable} target="basic" runner={runner} save={save} />
        )}
        {section.section === "MILITARY" && (
          <SingleForm
            fields={MILITARY_FIELDS}
            data={{ value: section.value }}
            editable={editable}
            target="military"
            runner={runner}
            save={save}
          />
        )}
        {section.section === "SKILLS" && (
          <>
            <SubTitle>요약 문장</SubTitle>
            <SentenceList sentences={section.summary} editable={editable} runner={runner} save={save} />
            <SubTitle>기술 태그</SubTitle>
            <TagEditor tags={section.tags} editable={editable} runner={runner} save={save} />
          </>
        )}
        {section.section === "PROJECTS" &&
          (section.items.length === 0 ? (
            <p className="text-[13px] text-ink-muted">아직 등록된 경험이 없어요</p>
          ) : (
            section.items.map((project) => (
              <div key={project.itemId} className="flex flex-col gap-2 rounded-xl border-[1.5px] border-line p-3">
                <ProjectInfo project={project} editable={editable} runner={runner} save={save} />
                <SentenceList sentences={project.bullets} editable={editable} runner={runner} save={save} />
              </div>
            ))
          ))}
        {section.section === "PORTFOLIO" && (
          <>
            {section.items.length === 0 ? (
              <p className="text-[13px] text-ink-muted">등록된 파일이 없어요</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {section.items.map((item) => (
                  <li key={item.itemId} className="flex flex-col gap-1.5 rounded-[10px] border-[1.5px] border-line px-3 py-2.5">
                    <label className="flex items-center gap-2 text-sm text-ink">
                      <span className="min-w-0 flex-1 break-all">{item.fileName}</span>
                      <span className="shrink-0 text-xs text-ink-sub">{item.include ? "첨부" : "첨부 안 함"}</span>
                      <input
                        type="checkbox"
                        checked={item.include}
                        disabled={!editable || runner.saving}
                        onChange={(e) =>
                          save(item.itemId, {
                            op: "UPDATE_ITEM",
                            itemId: item.itemId,
                            item: { include: e.target.checked },
                          })
                        }
                        aria-label={`${item.fileName} 첨부`}
                        className="size-5 shrink-0 accent-primary"
                      />
                    </label>
                    <ErrorText error={runner.error} target={item.itemId} />
                  </li>
                ))}
              </ul>
            )}
            {editable && <p className="text-[11px] text-ink-muted">파일 추가 · 교체는 내 프로필에서 할 수 있어요.</p>}
          </>
        )}
        {editable && showSaveToProfile && (
          <SaveToProfileCheckbox checked={saveToProfile} onChange={setSaveToProfile} disabled={runner.saving} />
        )}
      </div>
    </BottomSheet>
  );
}

function SubTitle({ children }: { children: React.ReactNode }) {
  return <h3 className="text-[13px] font-bold text-ink">{children}</h3>;
}

/** 항목이 하나뿐인 섹션(기본정보 · 병역). 바뀐 필드만 itemId null 로 저장 (M8 도 사용) */
export function SingleForm({
  fields,
  data,
  editable,
  target,
  runner,
  save,
}: {
  fields: FieldDef[];
  data: object;
  editable: boolean;
  target: string;
  runner: Runner;
  save: Save;
}) {
  // original 은 저장 성공 뒤 서버 응답으로 바뀐 최신 값
  const original = toFormValues(fields, data);
  const [values, setValues] = useState<FormValues>(original);
  const changed = hasChanges(fields, original, values);

  return (
    <div className="flex flex-col gap-2">
      <Fields fields={fields} values={values} onChange={setValues} idPrefix={target} disabled={!editable} />
      {editable && (
        <>
          <ErrorText error={runner.error} target={target} />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => {
                setValues(original);
                runner.clearError();
              }}
              disabled={runner.saving || !changed}
              className={cancelButton}
            >
              되돌리기
            </button>
            <button
              type="button"
              onClick={() =>
                save(target, { op: "UPDATE_ITEM", itemId: null, item: changedItemInput(fields, original, values) })
              }
              disabled={runner.saving || !changed || !isFormValid(fields, values)}
              aria-busy={runner.saving}
              className={applyButton}
            >
              {runner.saving ? "저장 중…" : "적용"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/** 문장 목록. 수정 → UPDATE_SENTENCE 한 건. 한 번에 한 문장만 고친다 */
function SentenceList({
  sentences,
  editable,
  runner,
  save,
}: {
  sentences: Sentence[];
  editable: boolean;
  runner: Runner;
  save: Save;
}) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");

  if (sentences.length === 0) return <p className="text-[13px] text-ink-muted">문장이 없어요</p>;

  const close = () => {
    setEditingId(null);
    runner.clearError();
  };

  return (
    <ul className="flex flex-col gap-2">
      {sentences.map((s) =>
        editingId === s.sentenceId ? (
          <li key={s.sentenceId} className="flex flex-col gap-2 rounded-[10px] border-[1.5px] border-primary p-3">
            <label className="flex flex-col gap-1 text-xs text-ink-sub">
              문장 고치기
              <textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                rows={3}
                className={`${inputClass} resize-none py-2.5`}
              />
            </label>
            {s.source !== "USER" && (
              <span className="text-[11px] text-ink-muted">고친 문장은 &apos;직접 입력&apos;으로 표시돼요</span>
            )}
            <ErrorText error={runner.error} target={s.sentenceId} />
            <div className="flex gap-2">
              <button type="button" onClick={close} disabled={runner.saving} className={cancelButton}>
                취소
              </button>
              <button
                type="button"
                onClick={() =>
                  save(s.sentenceId, { op: "UPDATE_SENTENCE", sentenceId: s.sentenceId, text: draft.trim() }, close)
                }
                disabled={runner.saving || draft.trim() === "" || draft.trim() === s.text}
                aria-busy={runner.saving}
                className={applyButton}
              >
                {runner.saving ? "저장 중…" : "적용"}
              </button>
            </div>
          </li>
        ) : (
          <li
            key={s.sentenceId}
            className="flex items-start gap-2 rounded-[10px] border-[1.5px] border-line py-2.5 pl-3 pr-1.5"
          >
            <span className="min-w-0 flex-1 text-sm leading-relaxed text-ink">{s.text}</span>
            <span className="mt-0.5 flex shrink-0 items-center gap-1">
              <SourceTag source={s.source} />
              {editable && (
                <IconButton
                  label="문장 수정"
                  onClick={() => {
                    setEditingId(s.sentenceId);
                    setDraft(s.text);
                    runner.clearError();
                  }}
                  disabled={runner.saving || editingId !== null}
                >
                  <PencilIcon />
                </IconButton>
              )}
            </span>
          </li>
        ),
      )}
    </ul>
  );
}

/** 프로젝트 이름 · 기간 · 인원 · 역할. 바뀐 필드만 프로젝트 itemId 로 저장 (bullets 는 문장 수정으로) */
function ProjectInfo({
  project,
  editable,
  runner,
  save,
}: {
  project: ProjectsSection["items"][number];
  editable: boolean;
  runner: Runner;
  save: Save;
}) {
  const [open, setOpen] = useState(false);
  const [values, setValues] = useState<FormValues>({});
  const original = toFormValues(PROJECT_FIELDS, project);
  const target = `${project.itemId}-info`;
  const meta = [project.periodText, project.teamSize !== null ? `${project.teamSize}명` : null, project.role]
    .filter(Boolean)
    .join(" · ");
  const close = () => {
    setOpen(false);
    runner.clearError();
  };

  if (open) {
    return (
      <div className="flex flex-col gap-2 rounded-[10px] border-[1.5px] border-primary p-3">
        <Fields fields={PROJECT_FIELDS} values={values} onChange={setValues} idPrefix={project.itemId} />
        <ErrorText error={runner.error} target={target} />
        <div className="flex gap-2">
          <button type="button" onClick={close} disabled={runner.saving} className={cancelButton}>
            취소
          </button>
          <button
            type="button"
            onClick={() =>
              save(
                target,
                { op: "UPDATE_ITEM", itemId: project.itemId, item: changedItemInput(PROJECT_FIELDS, original, values) },
                close,
              )
            }
            disabled={
              runner.saving || !isFormValid(PROJECT_FIELDS, values) || !hasChanges(PROJECT_FIELDS, original, values)
            }
            aria-busy={runner.saving}
            className={applyButton}
          >
            {runner.saving ? "저장 중…" : "적용"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start gap-2">
      <span className="flex min-w-0 flex-1 flex-col">
        <b className="break-all text-sm text-ink">{project.title}</b>
        {meta && <small className="text-xs text-ink-sub">{meta}</small>}
      </span>
      {editable && (
        <IconButton
          label={`${project.title} 정보 수정`}
          onClick={() => {
            setValues(original);
            setOpen(true);
            runner.clearError();
          }}
          disabled={runner.saving}
        >
          <PencilIcon />
        </IconButton>
      )}
    </div>
  );
}

/** 기술 태그. 추가 · 빼기를 모아 두었다가 "태그 적용"을 누르면 전체 목록을 itemId null 로 저장 (M8 도 사용) */
export function TagEditor({ tags: saved, editable, runner, save }: { tags: string[]; editable: boolean; runner: Runner; save: Save }) {
  const [tags, setTags] = useState(saved);
  const changed = tags.length !== saved.length || tags.some((t, i) => t !== saved[i]);

  return (
    <div className="flex flex-col gap-2">
      <TagInput tags={tags} onChange={setTags} disabled={!editable || runner.saving} label="기술 태그" />
      {editable && (
        <>
          <ErrorText error={runner.error} target="tags" />
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => {
                setTags(saved);
                runner.clearError();
              }}
              disabled={runner.saving || !changed}
              className={cancelButton}
            >
              되돌리기
            </button>
            <button
              type="button"
              onClick={() => save("tags", { op: "UPDATE_ITEM", itemId: null, item: { tags } })}
              disabled={runner.saving || !changed}
              aria-busy={runner.saving}
              className={applyButton}
            >
              {runner.saving ? "저장 중…" : "태그 적용"}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
