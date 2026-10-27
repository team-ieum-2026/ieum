// 주인: FE-B — M6-2 섹션 편집 시트(FE-B-05)의 섹션별 입력칸 정의. M6 · M8 공용
// 필드 이름 · 값은 docs/API.md 3-3 서류 JSON 과 db/schema.sql 주석을 따른다. itemId · source 는 서버가 채운다.
// SECTION_FIELDS 는 추가 · 수정 · 삭제 요청 형식이 정해진 목록 섹션(학력 · 경력 · 자격증).
// BASIC_FIELDS · MILITARY_FIELDS · PROJECT_FIELDS 는 입력 화면용 (저장 형식은 BE-A 확인 전, SectionDetailSheet).
import type { CareerItem, CertificateItem, EducationItem } from "@/types/resume";

export type EditableSectionName = "EDUCATION" | "CAREER" | "CERTIFICATES";
export type EditableItem = EducationItem | CareerItem | CertificateItem;

export interface FieldDef {
  key: string;
  label: string;
  kind: "text" | "date" | "select" | "textarea" | "number";
  required?: boolean;
  placeholder?: string;
  /** 입력칸 아래 작은 안내 */
  hint?: string;
  options?: { value: string; label: string }[];
  /** true 면 옆 칸과 한 줄에 반씩 */
  half?: boolean;
}

export interface SectionFieldConfig {
  /** "자격증 추가" 처럼 쓰는 이름 */
  noun: string;
  fields: FieldDef[];
  /** 목록 한 줄의 굵은 글자 */
  title: (item: EditableItem) => string;
  /** 목록 한 줄의 작은 글자 */
  sub: (item: EditableItem) => string;
}

const DEGREE_OPTIONS = [
  { value: "HIGH_SCHOOL", label: "고등학교" },
  { value: "ASSOCIATE", label: "전문학사" },
  { value: "BACHELOR", label: "학사" },
  { value: "MASTER", label: "석사" },
  { value: "DOCTOR", label: "박사" },
];

const EDUCATION_STATUS_OPTIONS = [
  { value: "GRADUATED", label: "졸업" },
  { value: "EXPECTED", label: "졸업 예정" },
  { value: "ENROLLED", label: "재학" },
  { value: "DROPPED", label: "중퇴" },
];

const labelOf = (options: { value: string; label: string }[], value: string | null) =>
  value ? (options.find((o) => o.value === value)?.label ?? value) : null;

/** "2026-03-20" → "2026.03" */
const month = (date: string | null) => (date ? date.slice(0, 7).replace("-", ".") : null);

const join = (parts: (string | null)[]) => parts.filter(Boolean).join(" · ");

export const SECTION_FIELDS: Record<EditableSectionName, SectionFieldConfig> = {
  CERTIFICATES: {
    noun: "자격증",
    fields: [
      { key: "name", label: "자격증 이름", kind: "text", required: true, placeholder: "예: AWS Cloud Practitioner" },
      { key: "acquiredOn", label: "취득일", kind: "date", half: true },
      { key: "issuer", label: "발급 기관", kind: "text", placeholder: "발급 기관", half: true },
    ],
    title: (item) => (item as CertificateItem).name,
    sub: (item) => {
      const i = item as CertificateItem;
      return join([month(i.acquiredOn), i.issuer]);
    },
  },
  EDUCATION: {
    noun: "학력",
    fields: [
      { key: "schoolName", label: "학교", kind: "text", required: true, placeholder: "예: ○○대학교" },
      { key: "major", label: "전공", kind: "text", placeholder: "예: 컴퓨터공학과" },
      { key: "degree", label: "학위", kind: "select", options: DEGREE_OPTIONS, half: true },
      { key: "status", label: "학적", kind: "select", options: EDUCATION_STATUS_OPTIONS, half: true },
      { key: "graduatedOn", label: "졸업(예정)일", kind: "date" },
    ],
    title: (item) => {
      const i = item as EducationItem;
      return join([i.schoolName, i.major]);
    },
    sub: (item) => {
      const i = item as EducationItem;
      return join([labelOf(DEGREE_OPTIONS, i.degree), labelOf(EDUCATION_STATUS_OPTIONS, i.status), month(i.graduatedOn)]);
    },
  },
  CAREER: {
    noun: "경력",
    fields: [
      { key: "companyName", label: "회사", kind: "text", required: true, placeholder: "회사 이름" },
      { key: "position", label: "직무", kind: "text", placeholder: "예: 백엔드 인턴" },
      { key: "startedOn", label: "시작일", kind: "date", half: true },
      { key: "endedOn", label: "종료일", kind: "date", half: true, hint: "재직 중이면 비워 두세요" },
      { key: "description", label: "한 일", kind: "textarea", placeholder: "맡은 일을 한두 줄로" },
    ],
    title: (item) => {
      const i = item as CareerItem;
      return join([i.companyName, i.position]);
    },
    sub: (item) => {
      const i = item as CareerItem;
      if (!i.startedOn && !i.endedOn) return "";
      return `${month(i.startedOn) ?? ""} ~ ${month(i.endedOn) ?? "재직 중"}`.trim();
    },
  },
};

export const isEditableSection = (section: string): section is EditableSectionName => section in SECTION_FIELDS;

/** BASIC.fields (docs/API.md 3-3) */
export const BASIC_FIELDS: FieldDef[] = [
  { key: "name", label: "이름", kind: "text", required: true },
  { key: "email", label: "이메일", kind: "text", placeholder: "example@email.com" },
  { key: "githubUrl", label: "GitHub", kind: "text", placeholder: "github.com/아이디" },
  { key: "desiredJob", label: "희망 직무", kind: "text", placeholder: "예: 백엔드 개발" },
];

/** MILITARY.value — db/schema.sql student.military_status. 빈 칸(선택 안 함)은 null */
export const MILITARY_FIELDS: FieldDef[] = [
  {
    key: "value",
    label: "병역",
    kind: "select",
    options: [
      { value: "SERVED", label: "군필" },
      { value: "NOT_SERVED", label: "미필" },
      { value: "EXEMPT", label: "면제" },
      { value: "NOT_APPLICABLE", label: "해당 없음" },
    ],
    hint: "선택 입력 · 비워 둬도 제출할 수 있어요",
  },
];

/** PROJECTS 항목의 문장(bullets) 밖 정보 (docs/API.md 3-3) */
export const PROJECT_FIELDS: FieldDef[] = [
  { key: "title", label: "프로젝트 이름", kind: "text", required: true },
  { key: "periodText", label: "기간", kind: "text", placeholder: "예: 6주", half: true },
  { key: "teamSize", label: "인원", kind: "number", placeholder: "예: 4", half: true },
  { key: "role", label: "역할", kind: "text", placeholder: "예: 백엔드" },
];

export type FormValues = Record<string, string>;

/** 데이터 → 입력칸 값 (null 은 빈 칸, 숫자는 글자로) */
export function toFormValues(fields: FieldDef[], data: object | null): FormValues {
  const record = (data ?? {}) as Record<string, unknown>;
  return Object.fromEntries(
    fields.map((f) => {
      const v = record[f.key];
      return [f.key, typeof v === "string" ? v : typeof v === "number" ? String(v) : ""];
    }),
  );
}

/** 입력칸 값 하나 → 요청 값. 빈 칸은 null, 숫자 칸은 숫자로 */
const toValue = (f: FieldDef, raw: string | undefined) => {
  const v = (raw ?? "").trim();
  if (v === "") return null;
  return f.kind === "number" ? Number(v) : v;
};

/** ADD_ITEM 용 item: 모든 필드. 빈 칸은 키를 빼지 않고 null (docs/API.md 공통 JSON 규칙) */
export function toItemInput(fields: FieldDef[], values: FormValues): Record<string, string | number | null> {
  return Object.fromEntries(fields.map((f) => [f.key, toValue(f, values[f.key])]));
}

/** UPDATE_ITEM 용 item: 바뀐 필드만 (생략 = 그대로, null = 비우기. docs/API.md 2-1). 바뀐 게 없으면 빈 객체 */
export function changedItemInput(
  fields: FieldDef[],
  original: FormValues,
  values: FormValues,
): Record<string, string | number | null> {
  return Object.fromEntries(
    fields
      .filter((f) => toValue(f, original[f.key]) !== toValue(f, values[f.key]))
      .map((f) => [f.key, toValue(f, values[f.key])]),
  );
}

export const hasChanges = (fields: FieldDef[], original: FormValues, values: FormValues) =>
  Object.keys(changedItemInput(fields, original, values)).length > 0;

export const isFormValid = (fields: FieldDef[], values: FormValues) =>
  fields.every((f) => {
    const v = (values[f.key] ?? "").trim();
    if (f.required && v === "") return false;
    return f.kind !== "number" || v === "" || Number.isInteger(Number(v));
  });
