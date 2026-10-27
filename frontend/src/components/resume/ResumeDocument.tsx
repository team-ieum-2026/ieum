// 주인: FE-B — 이력서 문서 부품 (FE-B-01). 서류 JSON(docs/API.md 3-3) 섹션별 전체 표시 · 출처 태그 · 문장 선택. M6-1 · C4 공용
// 폭은 부모를 따른다 (모바일 · 데스크톱 공용). 선택 상태는 부모가 들고, 근거는 EvidencePanel 로 보여 준다.
// source 가 없는 곳(MILITARY · PORTFOLIO · PROJECTS 항목)에는 태그를 붙이지 않는다.
import SentenceText from "@/components/resume/SentenceText";
import SourceTag from "@/components/resume/SourceTag";
import {
  DEGREE_LABEL,
  EDUCATION_STATUS_LABEL,
  MILITARY_LABEL,
  SECTION_LABEL,
  toDate,
} from "@/components/resume/resumeLabels";
import type { ApplicationDocument, DocumentSection, SectionName, Sentence } from "@/types/resume";
import type { Source } from "@/types/student";

interface ResumeDocumentProps {
  document: ApplicationDocument;
  /** 보여 줄 섹션. 없으면 전체 (순서는 항상 API sections 순서) */
  sections?: SectionName[];
  selectedSentenceId?: string | null;
  /** 없으면 문장 선택 없이 읽기 전용 */
  onSelectSentence?: (sentence: Sentence) => void;
}

export default function ResumeDocument({
  document,
  sections,
  selectedSentenceId = null,
  onSelectSentence,
}: ResumeDocumentProps) {
  const shown = sections ? document.sections.filter((s) => sections.includes(s.section)) : document.sections;
  const renderSentence = (s: Sentence) => (
    <SentenceText
      key={s.sentenceId}
      sentence={s}
      selected={s.sentenceId === selectedSentenceId}
      onSelect={onSelectSentence}
    />
  );

  return (
    <div className="flex flex-col divide-y divide-line rounded-xl border border-line bg-surface">
      {shown.map((section) => (
        <SectionBlock key={section.section} section={section} renderSentence={renderSentence} />
      ))}
    </div>
  );
}

function SectionBlock({
  section,
  renderSentence,
}: {
  section: DocumentSection;
  renderSentence: (s: Sentence) => React.ReactNode;
}) {
  const sectionSource = "source" in section ? section.source : null;
  return (
    <section className="flex flex-col gap-2 px-3.5 py-3">
      <h3 className="flex items-center gap-1.5 text-sm font-bold">
        {SECTION_LABEL[section.section]}
        {sectionSource && <SourceTag source={sectionSource} />}
      </h3>
      <SectionBody section={section} renderSentence={renderSentence} />
    </section>
  );
}

function SectionBody({
  section,
  renderSentence,
}: {
  section: DocumentSection;
  renderSentence: (s: Sentence) => React.ReactNode;
}) {
  switch (section.section) {
    case "BASIC": {
      const { name, email, githubUrl, desiredJob } = section.fields;
      const rows: [string, string | null][] = [
        ["이름", name],
        ["이메일", email],
        ["GitHub", githubUrl],
        ["희망 직무", desiredJob],
      ];
      return (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[13px]">
          {rows
            .filter(([, value]) => value)
            .map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="text-ink-muted">{label}</dt>
                <dd className="break-all">{value}</dd>
              </div>
            ))}
        </dl>
      );
    }
    case "EDUCATION":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((i) => (
            <Item
              key={i.itemId}
              source={i.source}
              title={[i.schoolName, i.major].filter(Boolean).join(" ")}
              sub={[
                i.degree ? DEGREE_LABEL[i.degree] ?? i.degree : null,
                i.status ? EDUCATION_STATUS_LABEL[i.status] ?? i.status : null,
                i.graduatedOn ? toDate(i.graduatedOn) : null,
              ]}
            />
          ))}
        </ItemList>
      );
    case "TRAINING":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((i) => (
            <Item key={i.itemId} source={i.source} title={i.name} sub={[i.campus, period(i.startedOn, i.endedOn)]} />
          ))}
        </ItemList>
      );
    case "CAREER":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((i) => (
            <Item
              key={i.itemId}
              source={i.source}
              title={[i.companyName, i.position].filter(Boolean).join(" · ")}
              // endedOn 이 null 이면 재직 중 (types/resume.ts CareerItem)
              sub={[period(i.startedOn, i.endedOn, "재직 중")]}
              detail={i.description}
            />
          ))}
        </ItemList>
      );
    case "MILITARY":
      return section.value === null ? (
        <p className="text-[13px] text-ink-muted">선택 입력 · 비워 둬도 제출 가능</p>
      ) : (
        <p className="text-[13px]">{MILITARY_LABEL[section.value]}</p>
      );
    case "CERTIFICATES":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((i) => (
            <Item
              key={i.itemId}
              source={i.source}
              title={i.name}
              sub={[i.issuer, i.acquiredOn ? toDate(i.acquiredOn) : null]}
            />
          ))}
        </ItemList>
      );
    case "SKILLS":
      return (
        <div className="flex flex-col gap-2">
          {section.summary.length > 0 && (
            <p className="text-[13px] leading-[1.9]">{section.summary.map(renderSentence)}</p>
          )}
          {section.tags.length > 0 && (
            <ul className="flex flex-wrap gap-1.5" aria-label="기술 태그">
              {section.tags.map((tag) => (
                <li key={tag} className="rounded-md bg-page px-2 py-0.5 text-xs text-ink-sub">
                  {tag}
                </li>
              ))}
            </ul>
          )}
          {section.summary.length === 0 && section.tags.length === 0 && <EmptyText />}
        </div>
      );
    case "PROJECTS":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((p) => (
            <li key={p.itemId} className="flex flex-col gap-1">
              <span className="text-[13px] font-bold">{p.title}</span>
              <span className="text-xs text-ink-muted">
                {[p.periodText, p.teamSize !== null ? `${p.teamSize}명` : null, p.role].filter(Boolean).join(" · ")}
              </span>
              {p.bullets.length > 0 && (
                <ul className="flex list-disc flex-col gap-1 pl-4 text-[13px] leading-[1.9]">
                  {p.bullets.map((b) => (
                    <li key={b.sentenceId}>{renderSentence(b)}</li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ItemList>
      );
    case "PORTFOLIO":
      return (
        <ItemList empty={section.items.length === 0}>
          {section.items.map((i) => (
            <li key={i.itemId} className="flex items-center justify-between gap-2 text-[13px]">
              <span className="min-w-0 break-all">{i.fileName}</span>
              <span className="shrink-0 text-xs text-ink-muted">{i.include ? "첨부" : "첨부 안 함"}</span>
            </li>
          ))}
        </ItemList>
      );
    case "COVER_LETTER":
      return section.sentences.length > 0 ? (
        <p className="text-sm leading-[2]">
          {section.sentences.map((s, i) => (
            <span key={s.sentenceId}>
              {i > 0 && " "}
              {renderSentence(s)}
            </span>
          ))}
        </p>
      ) : (
        <EmptyText />
      );
  }
}

function ItemList({ empty, children }: { empty: boolean; children: React.ReactNode }) {
  return empty ? <EmptyText /> : <ul className="flex flex-col gap-2.5">{children}</ul>;
}

function Item({
  source,
  title,
  sub,
  detail = null,
}: {
  source: Source;
  title: string;
  sub: (string | null)[];
  detail?: string | null;
}) {
  const subText = sub.filter(Boolean).join(" · ");
  return (
    <li className="flex items-start justify-between gap-2">
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[13px] font-bold">{title}</span>
        {subText && <span className="text-xs text-ink-muted">{subText}</span>}
        {detail && <span className="text-xs text-ink-sub">{detail}</span>}
      </span>
      <SourceTag source={source} />
    </li>
  );
}

function EmptyText() {
  return <p className="text-[13px] text-ink-muted">등록된 내용이 없어요</p>;
}

/** "2026-03-02 ~ 2026-08-29". 끝이 없으면 ongoing 문구 */
function period(start: string | null, end: string | null, ongoing?: string) {
  if (!start && !end) return null;
  const endText = end ? toDate(end) : (ongoing ?? "");
  return `${start ? toDate(start) : ""} ~ ${endText}`.trim();
}
