"use client";

import Link from "next/link";
import { useRef, type CSSProperties } from "react";
import StudentEntry from "./StudentEntry";
import StudentIcon from "./StudentIcon";

const headingLines = ["배움의 다음은,", "당신의 가능성."] as const;

function revealCharacters(text: string, offset = 0) {
  return Array.from(text).map((character, index) => <span
    key={index}
    aria-hidden="true"
    className={`entry-heading-character${character === "," || character === "." ? " entry-heading-punctuation" : ""}`}
    style={{ "--entry-character-index": offset + index } as CSSProperties}
  >{character}</span>);
}

export default function EntryScreen() {
  const helpRef = useRef<HTMLDialogElement>(null);
  const openHelp = () => helpRef.current?.showModal();

  return (
    <div className="entry-app">
      <main className="entry-page">
        <header className="header">
          <Link className="brand" href="/" aria-label="이음 시작 화면">
            <span className="brand-mark"><StudentIcon name="sparkles" className="icon" /></span>
            <span>이음</span>
          </Link>
        </header>
        <div className="entry-layout">
        <div className="entry-copy">
        <section className="hero" aria-labelledby="entry-heading">
          <h1 id="entry-heading" aria-label={headingLines.join(" ")}>{revealCharacters(headingLines[0])}<br /><em className="entry-heading-second-line">{revealCharacters(headingLines[1], headingLines[0].length)}</em></h1>
        </section>

        <section className="roles" aria-labelledby="role-select-heading">
          <h2 id="role-select-heading">어떤 역할로 시작할까요?</h2>
          <div className="role-list">
            <StudentEntry onNeedHelp={openHelp} />
            <Link className="role-card coordinator-card" href="/coordinator">
              <span className="role-icon"><StudentIcon name="people" className="icon" /></span>
              <span className="role-copy"><strong>코디네이터</strong><span>수료생 현황과 지원 업무 관리</span></span>
              <StudentIcon name="chevron" className="icon role-arrow" />
            </Link>
          </div>
        </section>
        </div>
        </div>

        <p className="entry-service-caption">청년취업사관학교 사후관리 서비스</p>
      </main>

      <dialog ref={helpRef} className="entry-help-dialog" aria-labelledby="entry-help-heading" onClick={(event) => {
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) event.currentTarget.close();
      }}>
        <button className="dialog-close" type="button" aria-label="닫기" onClick={() => helpRef.current?.close()}><StudentIcon name="close" className="icon" /></button>
        <span className="dialog-icon"><StudentIcon name="sprout" className="icon" /></span>
        <h2 id="entry-help-heading">이음, 이렇게 시작해요.</h2>
        <p>수료생은 역할 카드를 누른 뒤 안내받은 ID를 입력해주세요. ID를 모른다면 수료한 캠퍼스 담당 코디네이터에게 문의해주세요.</p>
        <p>코디네이터는 코디네이터 카드를 선택해 시작할 수 있어요.</p>
        <button className="primary-button" type="button" onClick={() => helpRef.current?.close()}>확인했어요<StudentIcon name="check" className="icon" /></button>
      </dialog>
    </div>
  );
}
