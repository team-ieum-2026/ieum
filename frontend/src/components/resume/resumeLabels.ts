// 주인: FE-B — 서류 코드 값 → 화면 문구 (FE-B-01). 값 목록은 docs/DATA_SPEC.md · docs/API.md 1장
import type { SectionName } from "@/types/resume";
import type { MilitaryStatus } from "@/types/student";

export const SECTION_LABEL: Record<SectionName, string> = {
  BASIC: "기본정보",
  EDUCATION: "학력",
  TRAINING: "교육",
  CAREER: "경력",
  MILITARY: "병역",
  CERTIFICATES: "자격증",
  SKILLS: "기술",
  PROJECTS: "경험",
  PORTFOLIO: "포트폴리오",
  COVER_LETTER: "자기소개서",
};

export const DEGREE_LABEL: Record<string, string> = {
  HIGH_SCHOOL: "고등학교",
  ASSOCIATE: "전문학사",
  BACHELOR: "학사",
  MASTER: "석사",
  DOCTOR: "박사",
};

export const EDUCATION_STATUS_LABEL: Record<string, string> = {
  GRADUATED: "졸업",
  EXPECTED: "졸업 예정",
  ENROLLED: "재학",
  DROPPED: "중퇴",
};

export const MILITARY_LABEL: Record<Exclude<MilitaryStatus, null>, string> = {
  SERVED: "군필",
  NOT_SERVED: "미필",
  EXEMPT: "면제",
  NOT_APPLICABLE: "해당 없음",
};

/** "2026-10-24T08:00:00+09:00" · "2026-03-20" → "2026-10-24" (서울 시간 기준 날짜 부분) */
export const toDate = (value: string) => value.slice(0, 10);
