// 주인: FE-B — M6 섹션 카드의 요약 한 줄 · 출처 태그 계산 (FE-B-02)
import type { CoverLetterSection, DocumentSection, SectionName } from "@/types/resume";
import type { MilitaryStatus, Source } from "@/types/student";

export type CardSection = Exclude<DocumentSection, CoverLetterSection>;

export interface SectionSummary {
  section: SectionName;
  label: string;
  text: string;
  /** true 면 안내 문구(흐린 글자)로 표시 */
  isEmpty: boolean;
  /** null 이면 태그를 붙이지 않음 */
  source: Source | null;
  /** TRAINING 은 수료 과정 정보라 수정할 수 없음 (docs/API.md 2-1) */
  readOnly: boolean;
}

const STATUS_LABEL: Record<string, string> = {
  GRADUATED: "졸업",
  EXPECTED: "졸업 예정",
  ENROLLED: "재학",
  DROPPED: "중퇴",
};

const MILITARY_LABEL: Record<Exclude<MilitaryStatus, null>, string> = {
  SERVED: "군필",
  NOT_SERVED: "미필",
  EXEMPT: "면제",
  NOT_APPLICABLE: "해당 없음",
};

/** 직접 고친 항목이 하나라도 있으면 "직접 입력", 그다음 AI, 나머지는 등록 */
function pickSource(sources: Source[]): Source | null {
  if (sources.length === 0) return null;
  if (sources.includes("USER")) return "USER";
  if (sources.includes("AI")) return "AI";
  return "REGISTERED";
}

/** "정보처리기사 외 1건" */
function withMore(first: string, total: number, unit = "건") {
  return total > 1 ? `${first} 외 ${total - 1}${unit}` : first;
}

const filled = (text: string, source: Source | null) => ({ text, isEmpty: false, source });
const empty = (text: string) => ({ text, isEmpty: true, source: null });

function summarize(section: CardSection): Pick<SectionSummary, "label" | "text" | "isEmpty" | "source"> {
  switch (section.section) {
    case "BASIC": {
      const { name, email } = section.fields;
      return { label: "기본정보", ...filled([name, email].filter(Boolean).join(" · "), section.source) };
    }
    case "EDUCATION": {
      const [first] = section.items;
      if (!first) return { label: "학력", ...empty("추가하려면 누르세요") };
      const status = first.status ? STATUS_LABEL[first.status] ?? null : null;
      const year = first.graduatedOn ? ` (${first.graduatedOn.slice(0, 4)})` : "";
      const text = [first.schoolName, first.major, status].filter(Boolean).join(" ") + year;
      return {
        label: "학력",
        ...filled(withMore(text, section.items.length), pickSource(section.items.map((i) => i.source))),
      };
    }
    case "TRAINING": {
      const [first] = section.items;
      if (!first) return { label: "교육", ...empty("등록된 교육 과정이 없어요") };
      const text = first.endedOn ? `${first.name} 수료` : first.name;
      return {
        label: "교육",
        ...filled(withMore(text, section.items.length), pickSource(section.items.map((i) => i.source))),
      };
    }
    case "CAREER": {
      const [first] = section.items;
      if (!first) return { label: "경력", ...empty("신입이라면 비워 두세요") };
      const text = [first.companyName, first.position].filter(Boolean).join(" · ");
      return {
        label: "경력",
        ...filled(withMore(text, section.items.length), pickSource(section.items.map((i) => i.source))),
      };
    }
    case "MILITARY":
      // 병역은 출처 정보가 없어 태그를 붙이지 않는다. 비어 있어도 제출을 막지 않음 (FE-B-02 완료 기준)
      return section.value === null
        ? { label: "병역", ...empty("선택 입력 · 비워 둬도 제출 가능") }
        : { label: "병역", ...filled(MILITARY_LABEL[section.value], null) };
    case "CERTIFICATES": {
      const [first] = section.items;
      if (!first) return { label: "자격증", ...empty("추가하려면 누르세요") };
      return {
        label: "자격증",
        ...filled(withMore(first.name, section.items.length), pickSource(section.items.map((i) => i.source))),
      };
    }
    case "SKILLS": {
      if (section.tags.length > 0) {
        const shown = section.tags.slice(0, 3).join(" · ");
        const rest = section.tags.length - 3;
        return { label: "기술", ...filled(rest > 0 ? `${shown} 외 ${rest}개` : shown, section.source) };
      }
      const [first] = section.summary;
      return first
        ? { label: "기술", ...filled(first.text, section.source) }
        : { label: "기술", ...empty("추가하려면 누르세요") };
    }
    case "PROJECTS": {
      const [first] = section.items;
      if (!first) return { label: "경험", ...empty("추가하려면 누르세요") };
      const sources = section.items.flatMap((i) => i.bullets.map((b) => b.source));
      return { label: "경험", ...filled(withMore(first.title, section.items.length), pickSource(sources)) };
    }
    case "PORTFOLIO": {
      const included = section.items.filter((i) => i.include);
      const [first] = included;
      // 포트폴리오는 등록 자료(student_project)에서만 온다
      return first
        ? { label: "포트폴리오", ...filled(withMore(`${first.fileName} 첨부`, included.length), "REGISTERED") }
        : { label: "포트폴리오", ...empty("첨부한 파일이 없어요") };
    }
  }
}

export function summarizeSection(section: CardSection): SectionSummary {
  return { section: section.section, readOnly: section.section === "TRAINING", ...summarize(section) };
}
