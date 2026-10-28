"use client";
// 주인: FE-B — C4 전용 서류 요약 (FE-B-08). 와이어프레임처럼 이력서는 섹션마다 한두 줄, 자기소개서는 전체 문장
// M6 공용 부품(ResumeDocument)은 그대로 두고 C4 에서만 쓴다. 문장 표시는 FE-B-01 SentenceText 를 재사용.
// 없는 정보는 만들지 않고 "없음" 계열 문구로만 표시한다.
import SentenceText from "@/components/resume/SentenceText";
import SourceTag from "@/components/resume/SourceTag";
import { DEGREE_LABEL, EDUCATION_STATUS_LABEL, MILITARY_LABEL } from "@/components/resume/resumeLabels";
import type { ApplicationDocument, DocumentSection, Sentence } from "@/types/resume";
import type { CompanyInfoStatus, Source } from "@/types/student";

/** "2026-03-20" → "2026.03" */
const month = (date: string | null) => (date ? date.slice(0, 7).replace("-", ".") : null);
const join = (parts: (string | null | undefined | false)[], sep = " · ") => parts.filter(Boolean).join(sep);

/** 직접 고친 것이 하나라도 있으면 USER, 그다음 AI, 나머지는 등록 */
const pickSource = (sources: Source[]): Source | null =>
  sources.length === 0 ? null : sources.includes("USER") ? "USER" : sources.includes("AI") ? "AI" : "REGISTERED";

const COMPANY_INFO_NOTICE: Record<Exclude<CompanyInfoStatus, "FOUND">, string> = {
  PARTIAL: "기업 정보 일부만 확인",
  NOT_FOUND: "공고 외 정보 확인 불가",
};

/** 자기소개서 AI 문장(근거 있는 것) 번호 "AI 1" … — 오른쪽 근거 제목에도 같은 번호를 쓴다 */
export function coverLetterMarkers(document: ApplicationDocument): Map<string, string> {
  const cover = document.sections.find((s) => s.section === "COVER_LETTER");
  const markers = new Map<string, string>();
  if (cover?.section === "COVER_LETTER") {
    cover.sentences
      .filter((s) => s.source === "AI" && s.evidence.length > 0)
      .forEach((s, i) => markers.set(s.sentenceId, `AI ${i + 1}`));
  }
  return markers;
}

/**
 * 수료생이 직접 고친 것을 종류별로 센다 ("문장 1 · 자격증 1"). 서류의 source 만 보고 계산한다.
 * 문장: 자기소개서 · 기술 요약 · 프로젝트 문장 중 USER / 항목: 목록 섹션의 USER 항목 / 기본정보: 섹션 source USER
 */
export function userEditBreakdown(document: ApplicationDocument): { label: string; count: number }[] {
  let sentences = 0;
  const items: Partial<Record<string, number>> = {};
  const ITEM_LABEL: Partial<Record<DocumentSection["section"], string>> = {
    EDUCATION: "학력",
    TRAINING: "교육",
    CAREER: "경력",
    CERTIFICATES: "자격증",
  };
  for (const s of document.sections) {
    if (s.section === "COVER_LETTER") sentences += s.sentences.filter((x) => x.source === "USER").length;
    if (s.section === "SKILLS") sentences += s.summary.filter((x) => x.source === "USER").length;
    if (s.section === "PROJECTS") sentences += s.items.flatMap((p) => p.bullets).filter((x) => x.source === "USER").length;
    if (s.section === "BASIC" && s.source === "USER") items["기본정보"] = 1;
    const label = ITEM_LABEL[s.section];
    if (label && "items" in s) {
      const n = (s.items as { source?: Source }[]).filter((i) => i.source === "USER").length;
      if (n > 0) items[label] = n;
    }
  }
  return [
    ...(sentences > 0 ? [{ label: "문장", count: sentences }] : []),
    ...Object.entries(items).map(([label, count]) => ({ label, count: count ?? 0 })),
  ];
}

interface C4DocumentSummaryProps {
  document: ApplicationDocument;
  selectedSentenceId: string | null;
  onSelectSentence: (sentence: Sentence) => void;
}

export default function C4DocumentSummary({ document, selectedSentenceId, onSelectSentence }: C4DocumentSummaryProps) {
  const find = <N extends DocumentSection["section"]>(name: N) =>
    document.sections.find((s): s is Extract<DocumentSection, { section: N }> => s.section === name);
  const markers = coverLetterMarkers(document);
  const sentence = (s: Sentence, marker?: string) => (
    <SentenceText
      key={s.sentenceId}
      sentence={s}
      selected={s.sentenceId === selectedSentenceId}
      onSelect={onSelectSentence}
      marker={marker}
    />
  );

  const basic = find("BASIC");
  const education = find("EDUCATION");
  const training = find("TRAINING");
  const career = find("CAREER");
  const military = find("MILITARY");
  const certificates = find("CERTIFICATES");
  const skills = find("SKILLS");
  const projects = find("PROJECTS");
  const portfolio = find("PORTFOLIO");
  const cover = find("COVER_LETTER");

  // 기술 요약 · 프로젝트 문장은 접어 두고 필요할 때 펼쳐 근거를 본다
  const skillSentences = [...(skills?.summary ?? []), ...(projects?.items.flatMap((p) => p.bullets) ?? [])];

  return (
    // 정보는 짧게, 여백은 넉넉하게 (읽기 편한 문서). 한 화면 맞춤보다 가독성 우선
    <div className="flex flex-col gap-5 rounded-2xl border border-line bg-surface px-7 py-6">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-bold">이력서</h2>
        <span className="flex items-center gap-1">
          <SourceTag source="AI" />
          <SourceTag source="REGISTERED" label="등록 자료" />
          <SourceTag source="USER" />
        </span>
      </div>

      <dl className="flex flex-col divide-y divide-line text-sm leading-relaxed">
        {basic && (
          <Row label="기본정보" source={basic.source}>
            {join([
              basic.fields.name,
              basic.fields.email,
              basic.fields.githubUrl,
              basic.fields.desiredJob && `희망 직무 ${basic.fields.desiredJob}`,
            ])}
          </Row>
        )}
        {education && (
          <Row label="학력" source={pickSource(education.items.map((i) => i.source))}>
            {education.items
              .map((i) =>
                join(
                  [
                    join([i.schoolName, i.major], " "),
                    i.degree && (DEGREE_LABEL[i.degree] ?? i.degree),
                    i.status && (EDUCATION_STATUS_LABEL[i.status] ?? i.status),
                    i.graduatedOn && `(${month(i.graduatedOn)})`,
                  ],
                  " ",
                ),
              )
              .join(" · ")}
          </Row>
        )}
        {training && (
          <Row label="교육" source={pickSource(training.items.map((i) => i.source))}>
            {training.items
              .map((i) =>
                join(
                  [i.name, (i.startedOn || i.endedOn) && `(${month(i.startedOn) ?? ""} – ${month(i.endedOn) ?? ""})`],
                  " ",
                ),
              )
              .join(" · ")}
          </Row>
        )}
        {career && (
          <Row label="경력" source={pickSource(career.items.map((i) => i.source))} empty="등록된 경력 없음">
            {career.items
              .map((i) =>
                join(
                  [
                    join([i.companyName, i.position]),
                    (i.startedOn || i.endedOn) && `(${month(i.startedOn) ?? ""} – ${month(i.endedOn) ?? "재직 중"})`,
                  ],
                  " ",
                ),
              )
              .join(" · ")}
          </Row>
        )}
        {military && (
          <Row label="병역" source={null} empty="입력 안 함 (선택 항목)">
            {military.value ? MILITARY_LABEL[military.value] : ""}
          </Row>
        )}
        {certificates && (
          // 자격증은 항목마다 실제 source 로 태그 (목업: SQLD 만 "직접 입력")
          <Row label="자격증" source={null} isEmpty={certificates.items.length === 0}>
            <span className="flex flex-wrap gap-x-4 gap-y-1.5">
              {certificates.items.map((i) => (
                <span key={i.itemId} className="inline-flex items-center gap-1.5">
                  {join([i.name, i.acquiredOn && `(${month(i.acquiredOn)})`], " ")}
                  <SourceTag source={i.source} />
                </span>
              ))}
            </span>
          </Row>
        )}
        {(skills || projects) && (
          <Row
            label="기술 · 경험"
            source={skills?.source ?? null}
            extra={
              skillSentences.length > 0 && (
                <details className="mt-2">
                  <summary className="cursor-pointer text-[13px] font-bold text-primary">
                    AI 문장 {skillSentences.length}개 · 눌러서 근거 보기
                  </summary>
                  <ul className="mt-2 flex flex-col gap-2 leading-[1.9]">
                    {skillSentences.map((s) => (
                      <li key={s.sentenceId}>{sentence(s)}</li>
                    ))}
                  </ul>
                </details>
              )
            }
          >
            {join(
              [
                skills && skills.tags.length > 0 && skills.tags.join(", "),
                projects &&
                  projects.items.length > 0 &&
                  `프로젝트 ${projects.items.length}건 (${projects.items.map((p) => p.title).join(", ")})`,
              ],
              " · ",
            )}
          </Row>
        )}
        {portfolio && (
          <Row label="포트폴리오" source={portfolio.items.some((i) => i.include) ? "REGISTERED" : null} empty="첨부 파일 없음">
            {portfolio.items.map((i) => `${i.fileName}${i.include ? "" : " (첨부 안 함)"}`).join(" · ")}
          </Row>
        )}
      </dl>

      {cover && (
        <section aria-label="자기소개서" className="flex flex-col gap-4 border-t border-line pt-6">
          <h2 className="text-base font-bold">자기소개서</h2>
          {document.companyInfoStatus !== "FOUND" && (
            <p className="rounded-lg bg-warn-bg px-4 py-2.5 text-[13px] leading-relaxed text-warn-ink">
              <b>{COMPANY_INFO_NOTICE[document.companyInfoStatus]}</b>
              {document.companyInfoNote && ` · ${document.companyInfoNote}`}
            </p>
          )}
          {cover.sentences.length === 0 ? (
            <p className="text-[13px] text-ink-muted">작성된 문장이 없어요</p>
          ) : (
            // 본문은 15px · 줄 높이 2.3, 문장 사이에 조금 더 띄워 문장 단위로 끊어 읽기 쉽게
            <p className="mt-1 text-[15px] leading-[2.3]">
              {cover.sentences.map((s, i) => (
                <span key={s.sentenceId} className={i > 0 ? "ml-1.5" : undefined}>
                  {sentence(s, markers.get(s.sentenceId))}
                </span>
              ))}
            </p>
          )}
          {/* 범례: 색 견본은 SentenceText 의 AI · USER 강조 스타일과 같은 토큰 */}
          <ul className="flex items-center gap-5 text-[13px] text-ink-sub" aria-label="자기소개서 표시 설명">
            <li className="flex items-center gap-1.5">
              <span className="h-3.5 w-5 rounded-sm border-b-2 border-primary bg-primary-tint" aria-hidden="true" />
              AI 문장 · 근거 연결
            </li>
            <li className="flex items-center gap-1.5">
              <span className="h-3.5 w-5 rounded-sm border-b-2 border-warn-ink bg-warn-bg" aria-hidden="true" />
              수료생이 직접 수정
            </li>
          </ul>
        </section>
      )}
    </div>
  );
}

/**
 * 이력서 한 줄: 섹션 이름 · 요약 · 출처 태그. extra 는 요약 아래 덧붙임.
 * 요약이 빈 글자이거나 isEmpty 면 empty 문구 (글자가 아닌 요약은 isEmpty 로 알려 준다)
 */
function Row({
  label,
  source,
  sourceLabel,
  empty = "등록된 내용 없음",
  isEmpty,
  extra,
  children,
}: {
  label: string;
  source: Source | null;
  sourceLabel?: string;
  empty?: string;
  isEmpty?: boolean;
  extra?: React.ReactNode;
  children: React.ReactNode;
}) {
  const hasContent = isEmpty === undefined ? children !== "" : !isEmpty;
  return (
    <div className="grid grid-cols-[96px_minmax(0,1fr)_auto] items-start gap-x-4 py-3.5 first:pt-0 last:pb-0">
      <dt className="font-bold text-ink">{label}</dt>
      <dd className="min-w-0 break-words">
        <span className={hasContent ? "text-ink-sub" : "text-ink-muted"}>{hasContent ? children : empty}</span>
        {extra}
      </dd>
      <dd>{hasContent && source && <SourceTag source={source} label={sourceLabel} />}</dd>
    </div>
  );
}
