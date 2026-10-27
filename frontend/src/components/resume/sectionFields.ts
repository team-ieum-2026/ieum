// 주인: FE-B — M6-2 섹션 편집 시트(FE-B-05)의 섹션별 입력칸 정의. M6 · M8 공용
// 필드 이름 · 값은 docs/API.md 3-3 서류 JSON 과 db/schema.sql 주석을 따른다. itemId · source 는 서버가 채운다.
// 요청 형식이 정해진 목록 섹션(학력 · 경력 · 자격증)만 둔다. 나머지는 BE-A 확인 뒤 같은 틀로 추가.
import type { CareerItem, CertificateItem, EducationItem } from "@/types/resume";

export type EditableSectionName = "EDUCATION" | "CAREER" | "CERTIFICATES";
export type EditableItem = EducationItem | CareerItem | CertificateItem;

export interface FieldDef {
  key: string;
  label: string;
  kind: "text" | "date" | "select" | "textarea";
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

export type FormValues = Record<string, string>;

/** 항목 → 입력칸 값 (null 은 빈 칸) */
export function toFormValues(name: EditableSectionName, item: EditableItem | null): FormValues {
  const record = (item ?? {}) as unknown as Record<string, unknown>;
  return Object.fromEntries(
    SECTION_FIELDS[name].fields.map((f) => [f.key, typeof record[f.key] === "string" ? (record[f.key] as string) : ""]),
  );
}

/** 입력칸 값 → 요청 item. 빈 칸은 키를 빼지 않고 null (docs/API.md 공통 JSON 규칙) */
export function toItemInput(name: EditableSectionName, values: FormValues): Record<string, string | null> {
  return Object.fromEntries(
    SECTION_FIELDS[name].fields.map((f) => {
      const v = (values[f.key] ?? "").trim();
      return [f.key, v === "" ? null : v];
    }),
  );
}

export const isFormValid = (name: EditableSectionName, values: FormValues) =>
  SECTION_FIELDS[name].fields.every((f) => !f.required || (values[f.key] ?? "").trim() !== "");
