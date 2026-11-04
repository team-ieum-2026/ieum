"use client";
// 주인: FE-B — 행 전체를 눌러 상세로 가는 표 행 (C2 수료생 관리 · C4 서류 검토 공용). 표 머리 정렬은 없다.

/** 누를 수 있는 행 모양: 손가락 커서 · 연한 배경(마우스 · 키보드 초점) · 키보드 초점 테두리 */
export const rowLinkClass =
  "cursor-pointer border-b border-(--coord-line) transition-colors hover:bg-(--coord-hover) focus-visible:bg-(--coord-hover) focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-(--coord-green)";

/**
 * 행 전체를 눌러 상세로 가는 표 행 (C2 · C4). 마우스 클릭 · Tab 으로 행에 와서 Enter.
 * 행 안의 링크 · 버튼을 누르면 그 기능이 먼저이고 행 이동은 하지 않는다 (두 번 이동 방지)
 */
export function rowLinkProps(href: string, go: (href: string) => void, label: string) {
  return {
    tabIndex: 0,
    "aria-label": label,
    onClick: (e: React.MouseEvent<HTMLTableRowElement>) => {
      if ((e.target as HTMLElement).closest("a, button")) return;
      go(href);
    },
    onKeyDown: (e: React.KeyboardEvent<HTMLTableRowElement>) => {
      if (e.key === "Enter" && e.target === e.currentTarget) go(href);
    },
  };
}
