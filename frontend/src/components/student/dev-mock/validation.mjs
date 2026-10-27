// FE-A 개발 전용. 화면에서 import하지 않는다.
export class ApiFault extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function requireValue(condition, message = "요청 값을 확인해 주세요.", code = "INVALID_REQUEST", status = 400) {
  if (!condition) throw new ApiFault(status, code, message);
}

export function object(value) {
  requireValue(value !== null && typeof value === "object" && !Array.isArray(value));
  return value;
}

export function keys(value, allowed, required = []) {
  object(value);
  requireValue(Object.keys(value).every((key) => allowed.includes(key)) && required.every((key) => Object.hasOwn(value, key)));
}

export function text(value, nullable = false) {
  requireValue((nullable && value === null) || (typeof value === "string" && value.trim().length > 0));
  return value;
}

export function choice(value, allowed) {
  requireValue(allowed.includes(value));
  return value;
}

export function id(value) {
  requireValue(/^[1-9]\d*$/.test(String(value)) && Number.isSafeInteger(Number(value)));
  return Number(value);
}

const nullableString = (value) => value === null || typeof value === "string";
const stringList = (value) => Array.isArray(value) && value.every((entry) => typeof entry === "string" && entry.trim());
const date = (value) => value === null || (typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value)
  && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value);
const nullableEnum = (values) => (value) => value === null || values.includes(value);

// API 타입에 정의된 필드만 받는다. itemId/source/evidence는 서버가 관리한다.
export const itemRules = {
  BASIC: { name: (v) => typeof v === "string" && !!v.trim(), email: nullableString, githubUrl: nullableString, desiredJob: nullableString },
  EDUCATION: { schoolName: (v) => typeof v === "string" && !!v.trim(), major: nullableString,
    degree: nullableEnum(["HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR"]),
    status: nullableEnum(["GRADUATED", "EXPECTED", "ENROLLED", "DROPPED"]), graduatedOn: date },
  CAREER: { companyName: (v) => typeof v === "string" && !!v.trim(), position: nullableString, startedOn: date, endedOn: date, description: nullableString },
  CERTIFICATES: { name: (v) => typeof v === "string" && !!v.trim(), issuer: nullableString, acquiredOn: date },
  MILITARY: { value: nullableEnum(["SERVED", "NOT_SERVED", "EXEMPT", "NOT_APPLICABLE"]) },
  SKILLS: { tags: stringList },
  PROJECTS: { title: (v) => typeof v === "string" && !!v.trim(), periodText: nullableString,
    teamSize: (v) => v === null || (Number.isSafeInteger(v) && v > 0), role: nullableString, techStack: stringList },
  PORTFOLIO: { fileName: (v) => typeof v === "string" && !!v.trim(), include: (v) => typeof v === "boolean" },
};

export function validateItem(section, item, creating = false) {
  const rules = itemRules[section];
  requireValue(!!rules, "이 섹션은 해당 항목 API로 수정할 수 없어요.");
  keys(item, Object.keys(rules));
  requireValue(Object.keys(item).length > 0 && Object.entries(item).every(([key, value]) => rules[key](value)));
  const required = { BASIC: "name", EDUCATION: "schoolName", CAREER: "companyName", CERTIFICATES: "name", PROJECTS: "title", MILITARY: "value", SKILLS: "tags", PORTFOLIO: "fileName" };
  if (creating) requireValue(Object.hasOwn(item, required[section]));
  if (item.startedOn && item.endedOn) requireValue(item.startedOn <= item.endedOn);
}

export function record(section, input) {
  validateItem(section, input, true);
  return Object.fromEntries(Object.keys(itemRules[section]).map((key) => [key, input[key] ?? null]));
}

export const passReasons = ["ALREADY_EMPLOYED", "JOB_MISMATCH", "CONDITION_MISMATCH", "SKILL_GAP", "TAKING_BREAK", "OTHER"];
