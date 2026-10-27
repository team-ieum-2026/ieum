"use client";
// 주인: FE-B — M6-2 섹션 편집 바텀시트 (FE-B-05). 목록 섹션(학력 · 경력 · 자격증)의 항목 추가 · 수정 · 삭제
// 저장은 "+ 추가" · "적용" · "삭제"를 누를 때마다 한 건씩 (PATCH 는 한 번에 하나의 변경만, docs/API.md 2-1).
// 실제 저장 방법은 부모가 onChange 로 넘긴다 → M6 은 서류 PATCH, M8(FE-B-06)은 프로필 API 로 재사용.
import { useState } from "react";
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
import {
  DoneButton,
  ErrorText,
  Fields,
  IconButton,
  PencilIcon,
  SaveToProfileCheckbox,
  TrashIcon,
  applyButton,
  cancelButton,
  smallButton,
  useSaveRunner,
  type ItemChange,
} from "@/components/resume/sheetParts";

export type { ItemChange } from "@/components/resume/sheetParts";

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
  const [addValues, setAddValues] = useState<FormValues>(() => toFormValues(config.fields, null));
  const [saveToProfile, setSaveToProfile] = useState(true);
  const { saving, error, clearError, run } = useSaveRunner();

  const save = (target: string, change: ItemChange, onSuccess: () => void) =>
    run(target, () => onChange(change, showSaveToProfile ? saveToProfile : false), onSuccess);

  const startEdit = (item: EditableItem) => {
    setMode({ kind: "edit", itemId: item.itemId });
    setEditValues(toFormValues(config.fields, item));
    clearError();
  };
  const startDelete = (item: EditableItem) => {
    setMode({ kind: "delete", itemId: item.itemId });
    clearError();
  };
  const backToIdle = () => {
    setMode({ kind: "idle" });
    clearError();
  };

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
      actions={<DoneButton onClick={onClose} disabled={saving} />}
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
                    <Fields fields={config.fields} values={editValues} onChange={setEditValues} idPrefix={item.itemId} />
                    <ErrorText error={error} target={item.itemId} />
                    <div className="flex gap-2">
                      <button type="button" onClick={backToIdle} disabled={saving} className={cancelButton}>
                        취소
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          save(
                            item.itemId,
                            { op: "UPDATE_ITEM", itemId: item.itemId, item: toItemInput(config.fields, editValues) },
                            backToIdle,
                          )
                        }
                        disabled={saving || !isFormValid(config.fields, editValues)}
                        aria-busy={saving}
                        className={applyButton}
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
                          <PencilIcon />
                        </IconButton>
                        <IconButton label={`${title} 삭제`} onClick={() => startDelete(item)} disabled={saving}>
                          <TrashIcon />
                        </IconButton>
                      </>
                    )}
                  </div>
                  {deleting && (
                    <div className="flex flex-col gap-2 border-t border-line px-3 py-2.5">
                      <p className="text-[13px] font-bold text-ink">삭제할까요?</p>
                      <ErrorText error={error} target={item.itemId} />
                      <div className="flex gap-2">
                        <button type="button" onClick={backToIdle} disabled={saving} className={cancelButton}>
                          취소
                        </button>
                        <button
                          type="button"
                          onClick={() => save(item.itemId, { op: "DELETE_ITEM", itemId: item.itemId }, backToIdle)}
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
            <Fields fields={config.fields} values={addValues} onChange={setAddValues} idPrefix="add" />
            <ErrorText error={error} target="add" />
            <button
              type="button"
              onClick={() =>
                save("add", { op: "ADD_ITEM", item: toItemInput(config.fields, addValues) }, () =>
                  setAddValues(toFormValues(config.fields, null)),
                )
              }
              disabled={saving || mode.kind !== "idle" || !isFormValid(config.fields, addValues)}
              aria-busy={saving && mode.kind === "idle"}
              className="min-h-11 rounded-[10px] border-[1.5px] border-primary bg-surface text-sm font-bold text-primary disabled:border-line disabled:text-ink-muted"
            >
              {saving && mode.kind === "idle" ? "저장 중…" : "+ 추가"}
            </button>
          </div>
        )}

        {editable && showSaveToProfile && (
          <SaveToProfileCheckbox checked={saveToProfile} onChange={setSaveToProfile} disabled={saving} />
        )}
      </div>
    </BottomSheet>
  );
}
