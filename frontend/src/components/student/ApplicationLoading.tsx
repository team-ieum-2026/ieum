import Link from "next/link";
import StudentIcon from "./StudentIcon";

export default function ApplicationLoading({ back, fullHeight = false }: { back?: string; fullHeight?: boolean }) {
  return (
    <section className={`s-application-loading${fullHeight ? " s-application-loading-full" : ""}`}>
      {back && <Link className="s-back-link" href={back}><StudentIcon name="arrow" />지원 현황으로</Link>}
      <div className="s-application-loading-content" role="status" aria-live="polite" aria-atomic="true">
        <span className="s-application-spinner" aria-hidden="true" />
        <h1>지원서를 작성중입니다.</h1>
        <p>잠시만 기다려주세요</p>
      </div>
    </section>
  );
}
