"use client";

import Image from "next/image";
import Link from "next/link";
import { useRef } from "react";
import StudentEntry from "./StudentEntry";
import StudentIcon from "./StudentIcon";

export default function EntryScreen() {
  const helpRef = useRef<HTMLDialogElement>(null);
  const openHelp = () => helpRef.current?.showModal();

  return (
    <div className="entry-app">
      <main className="entry-page">
        <header className="header">
          <Link className="brand" href="/" aria-label="이음 시작 화면">
            <span className="brand-mark"><StudentIcon name="sprout" className="icon" /></span>
            <span>이음</span>
          </Link>
        </header>

        <section className="hero" aria-labelledby="entry-heading">
          <p className="eyebrow">YOUR NEXT CHAPTER</p>
          <h1 id="entry-heading">당신의 다음 기회를<br /><em>이음이 연결할게요.</em></h1>
          <p className="hero-description">교육 이후의 취업 준비부터<br className="mobile-break" /> 성장 관리까지</p>
          <div className="hero-art">
            <Image src="/images/entry-hero.png" width={1536} height={1024} sizes="(max-width: 700px) 100vw, 90vw" priority alt="새싹 캐릭터가 초록색 입체 이음 글씨에 기대어 웃고 있는 모습" />
          </div>
        </section>

        <section className="roles" aria-labelledby="role-select-heading">
          <h2 id="role-select-heading">어떤 역할로 시작하시나요?</h2>
          <div className="role-list">
            <StudentEntry onNeedHelp={openHelp} />
            <Link className="role-card coordinator-card" href="/coordinator">
              <span className="role-icon"><StudentIcon name="people" className="icon" /></span>
              <span className="role-copy"><strong>코디네이터</strong><span>수료생 현황과 지원 업무 관리</span></span>
              <StudentIcon name="chevron" className="icon role-arrow" />
            </Link>
          </div>
        </section>

        <footer className="footer">
          <p>청년취업사관학교 수료생 사후관리 서비스</p>
          <div><span><strong>SeSAC</strong><span className="footer-cross">×</span>이음</span><small>수료 후에도, 함께.</small></div>
        </footer>
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
