"use client";

import Link from "next/link";
import { useId, useState } from "react";
import type { RecommendationItemSummary, ResponseType } from "@/types/student";
import CareerImage from "./CareerImage";
import ResponseCancelButton from "./ResponseCancelButton";
import StudentIcon from "./StudentIcon";
import { deadlineLabel, studentHref } from "./navigation";

const responseLabels: Record<ResponseType, string> = {
  NONE: "미응답", APPLY: "지원 서류 준비", HELP: "도움 요청 완료", PASS: "패스",
};

export default function JobCard({ item, studentId, imageVariant, onCancelled }: {
  item: RecommendationItemSummary;
  studentId: number;
  imageVariant: number;
  onCancelled: () => void;
}) {
  // 펼치기 상태는 터치 화면의 명시적 버튼에만 사용한다. Hover·키보드 확장은 CSS가 맡는다.
  const [reasonsOpen, setReasonsOpen] = useState(false);
  const titleId = useId();
  const companyId = useId();
  const reasonsId = useId();
  const href = studentHref(`/student/jobs/${item.itemId}`, studentId);

  return <article className="s-job-card s-job-card-interactive" data-reasons-open={reasonsOpen} aria-labelledby={titleId}>
    <div className="s-card-top">
      <div className="s-response-actions">
        <span className="s-recommend-rank"><StudentIcon name="sparkles" />추천 {String(item.rank).padStart(2, "0")}</span>
        <span className={`s-response-badge s-response-${item.response}`}>{responseLabels[item.response]}</span>
        <ResponseCancelButton itemId={item.itemId} studentId={studentId} response={item.response} onCancelled={onCancelled} />
      </div>
      <span className={`s-deadline${item.job.dDay !== null && item.job.dDay <= 7 ? " is-urgent" : ""}`}>
        {deadlineLabel(item.job.dDay)}{item.job.untilFilled && <span className="block">채용 시 마감</span>}
      </span>
    </div>

    <Link className="s-job-card-link" href={href} aria-labelledby={`${companyId} ${titleId}`}>
      <CareerImage variant={imageVariant} jobTitle={item.job.title} />
      <div className="s-job-card-body">
        <div className="s-company">
          <span className="s-card-company-copy">
            <strong id={companyId} title={item.job.companyName}>{item.job.companyName}</strong>
            <small title={item.job.location}>{item.job.location}</small>
          </span>
        </div>
        <h3 id={titleId} title={item.job.title}>{item.job.title}</h3>
        <p className="s-job-meta"><StudentIcon name="briefcase" />{item.job.career} · {item.job.employmentType}</p>
      </div>
    </Link>

    <button className="s-card-reason-toggle" type="button" aria-expanded={reasonsOpen} aria-controls={reasonsId} aria-label={`${item.job.title} 추천 이유 ${reasonsOpen ? "접기" : "보기"}`} onClick={() => setReasonsOpen(value => !value)}>
      <span><StudentIcon name="sparkles" />추천 이유 {reasonsOpen ? "접기" : "보기"}</span><StudentIcon name="chevron" />
    </button>
    <div id={reasonsId} className="s-card-expansion">
      <div className="s-card-expansion-inner">
        <div className="s-card-expansion-content">
          <div className="s-reason-box">
            <p><StudentIcon name="sparkles" />이런 점이 잘 맞아요</p>
            {item.reasonSummary.length > 0
              ? <ul>{item.reasonSummary.slice(0, 2).map(reason => <li key={reason}><StudentIcon name="check" /><span title={reason}>{reason}</span></li>)}</ul>
              : <p className="s-card-no-reason">아직 제공된 추천 이유가 없어요.</p>}
          </div>
          <Link className="s-card-cta" href={href}>공고 자세히 보기<StudentIcon name="arrow" /></Link>
        </div>
      </div>
    </div>
  </article>;
}
