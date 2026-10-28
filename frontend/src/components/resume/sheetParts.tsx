"use client";
// 주인: FE-B — M6-2 섹션 편집 시트(FE-B-05) 공통 조각. 입력칸 · 아이콘 버튼 · 저장 한 건 실행 · 오류 문구 · 완료 버튼
// SectionEditSheet(목록 섹션)과 SectionDetailSheet(기본정보 · 병역 · 기술 · 경험 · 포트폴리오)가 같이 쓴다.
import { useRef, useState } from "react";
import { parseTags, type FieldDef, type FormValues } from "@/components/resume/sectionFields";
import { ApiError } from "@/lib/api/client";
import type { SectionItemInput } from "@/types/resume";

/** 시트가 부모에게 넘기는 저장 한 건. section · saveToProfile 은 부모가 붙인다 (docs/API.md 2-1 PATCH) */
export type ItemChange =
  | { op: "ADD_ITEM"; item: SectionItemInput }
  | { op: "UPDATE_ITEM"; itemId: string | null; item: SectionItemInput }
  | { op: "DELETE_ITEM"; itemId: string }
  | { op: "UPDATE_SENTENCE"; sentenceId: string; text: string };

export const inputClass =
  "min-h-11 w-full rounded-[10px] border-[1.5px] border-line bg-surface px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-primary disabled:bg-page disabled:text-ink-muted";
export const smallButton = "min-h-10 flex-1 rounded-lg text-[13px] font-bold";
export const cancelButton = `${smallButton} border-[1.5px] border-line bg-surface text-ink`;
export const applyButton = `${smallButton} bg-primary text-surface disabled:bg-line disabled:text-ink-muted`;

/**
 * 저장 한 건 실행. 저장 중 중복 요청을 막고, 실패하면 target 자리에 오류 문구를 남긴다 (입력값은 호출한 쪽이 유지)
 * target: "add" · itemId · sentenceId 처럼 오류를 보여 줄 자리
 */
export function useSaveRunner() {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<{ target: string; message: string } | null>(null);
  // 화면이 다시 그려지기 전 두 번 눌러도 요청이 한 번만 가도록
  const savingRef = useRef(false);

  const run = async (target: string, save: () => Promise<void>, onSuccess?: () => void) => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setError(null);
    try {
      await save();
      onSuccess?.();
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setError({ target, message: e instanceof ApiError ? e.message : "저장하지 못했어요. 다시 시도해 주세요." });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return { saving, error, clearError: () => setError(null), run };
}

export function ErrorText({ error, target }: { error: { target: string; message: string } | null; target: string }) {
  if (error?.target !== target) return null;
  return (
    <p role="alert" className="text-[13px] text-danger">
      {error.message}
    </p>
  );
}

/** 시트 맨 아래 "완료" (저장 중에는 닫지 않음) */
export function DoneButton({ onClick, disabled }: { onClick: () => void; disabled: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="flex min-h-[52px] flex-1 items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:bg-line disabled:text-ink-muted"
    >
      완료
    </button>
  );
}

/** "내 프로필에도 저장" 체크박스 (기본 선택은 부모 상태) */
export function SaveToProfileCheckbox({
  checked,
  onChange,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled: boolean;
}) {
  return (
    <label className="flex items-center gap-2 text-[13px] text-ink">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        disabled={disabled}
        className="size-4 accent-primary"
      />
      내 프로필에도 저장해서 다음 추천에 반영
    </label>
  );
}

/** 태그 목록 + 추가 입력칸 (× 로 빼기, Enter · "+ 추가"로 넣기). 기술 태그 · M8 프로젝트 기술 스택 공용 */
export function TagInput({
  tags,
  onChange,
  disabled = false,
  label,
  placeholder = "예: Docker",
}: {
  tags: string[];
  onChange: (tags: string[]) => void;
  disabled?: boolean;
  /** 화면 읽기 프로그램용 이름 (예: "기술 태그") */
  label: string;
  placeholder?: string;
}) {
  const [draft, setDraft] = useState("");
  const add = () => {
    const tag = draft.trim();
    if (tag && !tags.includes(tag)) onChange([...tags, tag]);
    setDraft("");
  };

  return (
    <div className="flex flex-col gap-2">
      {tags.length === 0 ? (
        <p className="text-[13px] text-ink-muted">등록된 태그가 없어요</p>
      ) : (
        <ul className="flex flex-wrap gap-1.5" aria-label={label}>
          {tags.map((tag) => (
            <li key={tag} className="flex items-center gap-0.5 rounded-md bg-page py-0.5 pl-2 pr-0.5 text-xs text-ink-sub">
              {tag}
              {!disabled && (
                <button
                  type="button"
                  aria-label={`${tag} 태그 빼기`}
                  onClick={() => onChange(tags.filter((t) => t !== tag))}
                  className="flex size-6 items-center justify-center text-ink-muted"
                >
                  ×
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {!disabled && (
        <div className="flex gap-2">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                e.preventDefault();
                add();
              }
            }}
            placeholder={placeholder}
            aria-label={`추가할 ${label}`}
            className={inputClass}
          />
          <button
            type="button"
            onClick={add}
            disabled={draft.trim() === ""}
            className="min-h-11 shrink-0 rounded-[10px] border-[1.5px] border-primary bg-surface px-4 text-sm font-bold text-primary disabled:border-line disabled:text-ink-muted"
          >
            + 추가
          </button>
        </div>
      )}
    </div>
  );
}

/** 입력칸 정의대로 그린다. half 칸은 두 개씩 한 줄 */
export function Fields({
  fields,
  values,
  onChange,
  idPrefix,
  disabled = false,
}: {
  fields: FieldDef[];
  values: FormValues;
  onChange: (values: FormValues) => void;
  idPrefix: string;
  /** true 면 읽기 전용 (예: 이미 검토를 요청한 서류) */
  disabled?: boolean;
}) {
  const set = (key: string, value: string) => onChange({ ...values, [key]: value });
  return (
    <div className="grid grid-cols-2 gap-2">
      {fields.map((f) => {
        const id = `${idPrefix}-${f.key}`;
        const value = values[f.key] ?? "";
        if (f.kind === "tags") {
          // 태그 칸 값은 JSON 배열 글자로 들고 있다 (sectionFields.ts toFormValues)
          return (
            <div key={f.key} id={id} className="col-span-2 flex flex-col gap-1 text-xs text-ink-sub">
              <span>{f.label}</span>
              <TagInput
                tags={parseTags(value)}
                onChange={(tags) => set(f.key, JSON.stringify(tags))}
                disabled={disabled}
                label={f.label}
                placeholder={f.placeholder}
              />
            </div>
          );
        }
        return (
          <label key={f.key} htmlFor={id} className={`flex flex-col gap-1 text-xs text-ink-sub ${f.half ? "" : "col-span-2"}`}>
            <span>
              {f.label}
              {f.required && <span className="text-danger"> *</span>}
            </span>
            {f.kind === "select" ? (
              <select
                id={id}
                value={value}
                onChange={(e) => set(f.key, e.target.value)}
                disabled={disabled}
                className={inputClass}
              >
                <option value="">선택 안 함</option>
                {f.options?.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            ) : f.kind === "textarea" ? (
              <textarea
                id={id}
                value={value}
                onChange={(e) => set(f.key, e.target.value)}
                placeholder={f.placeholder}
                disabled={disabled}
                rows={2}
                className={`${inputClass} resize-none py-2.5`}
              />
            ) : (
              <input
                id={id}
                type={f.kind === "date" ? "date" : f.kind === "number" ? "number" : "text"}
                inputMode={f.kind === "number" ? "numeric" : undefined}
                min={f.kind === "number" ? 1 : undefined}
                value={value}
                onChange={(e) => set(f.key, e.target.value)}
                placeholder={f.placeholder}
                required={f.required}
                disabled={disabled}
                className={inputClass}
              />
            )}
            {f.hint && <span className="text-[11px] text-ink-muted">{f.hint}</span>}
          </label>
        );
      })}
    </div>
  );
}

export function IconButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string;
  onClick: () => void;
  disabled: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      className="flex size-9 shrink-0 items-center justify-center rounded-lg text-ink-muted focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        {children}
      </svg>
    </button>
  );
}

export function PencilIcon() {
  return (
    <>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </>
  );
}

export function TrashIcon() {
  return (
    <>
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
    </>
  );
}
