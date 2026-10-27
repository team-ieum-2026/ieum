"use client";
// 주인: FE-B — M6-2 섹션 편집 바텀시트 (FE-B-05). 목록 섹션(학력 · 경력 · 자격증)의 항목 추가 · 수정 · 삭제
// 저장은 "+ 추가" · "적용" · "삭제"를 누를 때마다 한 건씩 (PATCH 는 한 번에 하나의 변경만, docs/API.md 2-1).
// 실제 저장 방법은 부모가 onChange 로 넘긴다 → M6 은 서류 PATCH, M8(FE-B-06)은 프로필 API 로 재사용.
import { useRef, useState } from "react";
import BottomSheet from "@/components/resume/BottomSheet";
import SourceTag from "@/components/resume/SourceTag";
import {
  SECTION_FIELDS,
  isFormValid,
  toFormValues,
  toItemInput,
  type EditableItem,
  type EditableSectionName,
  type FormValues,
} from "@/components/resume/sectionFields";
import { ApiError } from "@/lib/api/client";
import type { SectionItemInput } from "@/types/resume";

export type ItemChange =
  | { op: "ADD_ITEM"; item: SectionItemInput }
  | { op: "UPDATE_ITEM"; itemId: string; item: SectionItemInput }
  | { op: "DELETE_ITEM"; itemId: string };

interface SectionEditSheetProps {
  section: EditableSectionName;
  items: EditableItem[];
  /** false 면 목록만 보여 준다 (예: 이미 검토를 요청한 서류) */
  editable: boolean;
  /** "내 프로필에도 저장" 체크박스. M6 은 표시(기본 선택), M8 은 숨김 */
  showSaveToProfile?: boolean;
  /** 저장 한 건. 실패하면 throw → 시트가 입력값을 유지하고 오류를 보여 준다 */
  onChange: (change: ItemChange, saveToProfile: boolean) => Promise<void>;
  onClose: () => void;
}

type Mode = { kind: "idle" } | { kind: "edit"; itemId: string } | { kind: "delete"; itemId: string };

const inputClass =
  "min-h-11 w-full rounded-[10px] border-[1.5px] border-line bg-surface px-3 text-sm text-ink focus-visible:outline-2 focus-visible:outline-primary";
const smallButton = "min-h-10 flex-1 rounded-lg text-[13px] font-bold";

export default function SectionEditSheet({
  section,
  items,
  editable,
  showSaveToProfile = true,
  onChange,
  onClose,
}: SectionEditSheetProps) {
  const config = SECTION_FIELDS[section];
  const [mode, setMode] = useState<Mode>({ kind: "idle" });
  const [editValues, setEditValues] = useState<FormValues>({});
  const [addValues, setAddValues] = useState<FormValues>(() => toFormValues(section, null));
  const [saveToProfile, setSaveToProfile] = useState(true);
  const [saving, setSaving] = useState(false);
  /** target: "add" 또는 itemId — 오류를 실패한 자리에 보여 준다 */
  const [error, setError] = useState<{ target: string; message: string } | null>(null);
  // 화면이 다시 그려지기 전 두 번 눌러도 요청이 한 번만 가도록
  const savingRef = useRef(false);

  const run = async (change: ItemChange, target: string, onSuccess: () => void) => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setError(null);
    try {
      await onChange(change, showSaveToProfile ? saveToProfile : false);
      onSuccess();
    } catch (e: unknown) {
      // 서버 message 는 화면에 그대로 띄워도 되는 문장 (docs/API.md 1장)
      setError({ target, message: e instanceof ApiError ? e.message : "저장하지 못했어요. 다시 시도해 주세요." });
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const startEdit = (item: EditableItem) => {
    setMode({ kind: "edit", itemId: item.itemId });
    setEditValues(toFormValues(section, item));
    setError(null);
  };
  const startDelete = (item: EditableItem) => {
    setMode({ kind: "delete", itemId: item.itemId });
    setError(null);
  };
  const backToIdle = () => {
    setMode({ kind: "idle" });
    setError(null);
  };

  const errorText = (target: string) =>
    error?.target === target && (
      <p role="alert" className="text-[13px] text-danger">
        {error.message}
      </p>
    );

  return (
    <BottomSheet
      open
      title={config.noun}
      description={
        editable
          ? "추가 · 적용 · 삭제를 누르면 바로 저장돼요."
          : "검토를 요청한 서류라 지금은 내용만 확인할 수 있어요."
      }
      onClose={() => {
        if (!saving) onClose();
      }}
      actions={
        <button
          type="button"
          onClick={onClose}
          disabled={saving}
          className="flex min-h-[52px] flex-1 items-center justify-center rounded-xl bg-primary text-[15px] font-bold text-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:bg-line disabled:text-ink-muted"
        >
          완료
        </button>
      }
    >
      <div className="mt-4 flex flex-col gap-3">
        {items.length === 0 ? (
          <p className="text-[13px] text-ink-muted">아직 등록된 {config.noun}이 없어요</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {items.map((item) => {
              const title = config.title(item);
              const sub = config.sub(item);

              if (mode.kind === "edit" && mode.itemId === item.itemId) {
                return (
                  <li key={item.itemId} className="flex flex-col gap-2 rounded-[10px] border-[1.5px] border-primary p-3">
                    <Fields section={section} values={editValues} onChange={setEditValues} idPrefix={item.itemId} />
                    {errorText(item.itemId)}
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={backToIdle}
                        disabled={saving}
                        className={`${smallButton} border-[1.5px] border-line bg-surface text-ink`}
                      >
                        취소
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          run(
                            { op: "UPDATE_ITEM", itemId: item.itemId, item: toItemInput(section, editValues) },
                            item.itemId,
                            backToIdle,
                          )
                        }
                        disabled={saving || !isFormValid(section, editValues)}
                        aria-busy={saving}
                        className={`${smallButton} bg-primary text-surface disabled:bg-line disabled:text-ink-muted`}
                      >
                        {saving ? "저장 중…" : "적용"}
                      </button>
                    </div>
                  </li>
                );
              }

              const deleting = mode.kind === "delete" && mode.itemId === item.itemId;
              return (
                <li
                  key={item.itemId}
                  className={`flex flex-col rounded-[10px] border-[1.5px] bg-surface ${deleting ? "border-danger" : "border-line"}`}
                >
                  <div className="flex items-center gap-2 py-2.5 pl-3 pr-1.5">
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="break-all text-sm text-ink">{title}</span>
                      {sub && <small className="text-xs text-ink-sub">{sub}</small>}
                    </span>
                    <SourceTag source={item.source} />
                    {editable && (
                      <>
                        <IconButton label={`${title} 수정`} onClick={() => startEdit(item)} disabled={saving}>
                          <path d="M12 20h9" />
                          <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
                        </IconButton>
                        <IconButton label={`${title} 삭제`} onClick={() => startDelete(item)} disabled={saving}>
                          <path d="M3 6h18" />
                          <path d="M8 6V4h8v2" />
                          <path d="M19 6l-1 14H6L5 6" />
                        </IconButton>
                      </>
                    )}
                  </div>
                  {deleting && (
                    <div className="flex flex-col gap-2 border-t border-line px-3 py-2.5">
                      <p className="text-[13px] font-bold text-ink">삭제할까요?</p>
                      {errorText(item.itemId)}
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={backToIdle}
                          disabled={saving}
                          className={`${smallButton} border-[1.5px] border-line bg-surface text-ink`}
                        >
                          취소
                        </button>
                        <button
                          type="button"
                          onClick={() => run({ op: "DELETE_ITEM", itemId: item.itemId }, item.itemId, backToIdle)}
                          disabled={saving}
                          aria-busy={saving}
                          className={`${smallButton} bg-danger text-surface disabled:bg-line disabled:text-ink-muted`}
                        >
                          {saving ? "삭제 중…" : "삭제"}
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        {editable && (
          <div className="flex flex-col gap-2 rounded-xl bg-page p-3">
            <span className="text-[13px] font-bold text-ink">{config.noun} 추가</span>
            <Fields section={section} values={addValues} onChange={setAddValues} idPrefix="add" />
            {errorText("add")}
            <button
              type="button"
              onClick={() =>
                run({ op: "ADD_ITEM", item: toItemInput(section, addValues) }, "add", () =>
                  setAddValues(toFormValues(section, null)),
                )
              }
              disabled={saving || mode.kind !== "idle" || !isFormValid(section, addValues)}
              aria-busy={saving && mode.kind === "idle"}
              className="min-h-11 rounded-[10px] border-[1.5px] border-primary bg-surface text-sm font-bold text-primary disabled:border-line disabled:text-ink-muted"
            >
              {saving && mode.kind === "idle" ? "저장 중…" : "+ 추가"}
            </button>
          </div>
        )}

        {editable && showSaveToProfile && (
          <label className="flex items-center gap-2 text-[13px] text-ink">
            <input
              type="checkbox"
              checked={saveToProfile}
              onChange={(e) => setSaveToProfile(e.target.checked)}
              disabled={saving}
              className="size-4 accent-primary"
            />
            내 프로필에도 저장해서 다음 추천에 반영
          </label>
        )}
      </div>
    </BottomSheet>
  );
}

/** 섹션 정의대로 입력칸을 그린다. half 칸은 두 개씩 한 줄 */
function Fields({
  section,
  values,
  onChange,
  idPrefix,
}: {
  section: EditableSectionName;
  values: FormValues;
  onChange: (values: FormValues) => void;
  idPrefix: string;
}) {
  const set = (key: string, value: string) => onChange({ ...values, [key]: value });
  return (
    <div className="grid grid-cols-2 gap-2">
      {SECTION_FIELDS[section].fields.map((f) => {
        const id = `${idPrefix}-${f.key}`;
        const value = values[f.key] ?? "";
        return (
          <label key={f.key} htmlFor={id} className={`flex flex-col gap-1 text-xs text-ink-sub ${f.half ? "" : "col-span-2"}`}>
            <span>
              {f.label}
              {f.required && <span className="text-danger"> *</span>}
            </span>
            {f.kind === "select" ? (
              <select id={id} value={value} onChange={(e) => set(f.key, e.target.value)} className={inputClass}>
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
                rows={2}
                className={`${inputClass} resize-none py-2.5`}
              />
            ) : (
              <input
                id={id}
                type={f.kind === "date" ? "date" : "text"}
                value={value}
                onChange={(e) => set(f.key, e.target.value)}
                placeholder={f.placeholder}
                required={f.required}
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

function IconButton({
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
