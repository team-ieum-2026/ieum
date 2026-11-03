// 주인: FE-B — 수료생 명단 CSV (원래 C6 수료생 통합 현황에 있던 기능, 1차 피드백으로 C2 수료생 관리로 옮김).
// 목록 API 값 + 접수 중 도움 요청 수만 담는다. 전체 명단은 지금 검색 조건으로 100명씩 끝까지 받아 studentId 로 중복을 뺀다
import { manageLabels } from "@/components/coordinator/coordLabels";
import { subStatusLabels } from "@/components/student/labels";
import { getCoordinatorStudents } from "@/lib/api/coordinatorStudents";
import type { CoordStudentStatus } from "@/types/coordinator";
import type { CoordinatorStudentRow, StudentStatus, SubStatus } from "@/types/student";

/** 목록 API 가 허용하는 가장 큰 페이지 크기 (BE: size ≤ 100) */
const EXPORT_PAGE_SIZE = 100;

export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, "0")}${String(d.getDate()).padStart(2, "0")}`;
};

/** 지금 검색 조건의 전체 명단. 받는 중 인원이 바뀌어도 studentId 로 중복을 빼고, 빈 페이지가 오면 멈춘다 */
export async function fetchAllStudents(query: { status?: CoordStudentStatus; subStatus?: SubStatus; q: string }) {
  const rows = new Map<number, CoordinatorStudentRow>();
  let total = 0;
  for (let page = 1; ; page++) {
    // 목록 API 는 status=RESTING 을 지원한다 (docs/API.md). FE-A 쿼리 타입에 RESTING 이 아직 없어 여기서만 넘긴다
    const d = await getCoordinatorStudents({ status: query.status as StudentStatus | undefined, subStatus: query.subStatus, q: query.q, page, size: EXPORT_PAGE_SIZE });
    total = d.total;
    d.items.forEach((s) => rows.set(s.studentId, s));
    if (d.items.length === 0 || page * EXPORT_PAGE_SIZE >= d.total) break;
  }
  return { rows: [...rows.values()], total };
}

/** 엑셀에서 한글이 깨지지 않게 BOM 을 붙인다. 도움 요청 수를 모르면(null) 빈칸 */
export function downloadCsv(rows: CoordinatorStudentRow[], helpCount: (row: CoordinatorStudentRow) => number | null, fileName: string) {
  const cell = (v: string | number) => {
    const s = String(v);
    // 쉼표 · 따옴표 · 줄바꿈은 따옴표로 감싸고, 수식으로 읽히는 첫 글자는 막는다
    const safe = /^[=+\-@]/.test(s) ? `'${s}` : s;
    return /[",\n\r]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  const header = ["이름", "교육과정", "수료일", "관리 상태", "세부 상태", "최근 응답일", "비고 있음", "접수 중 도움 요청"];
  const lines = rows.map((s) =>
    [
      s.name,
      s.courseLabel,
      s.completedOn,
      manageLabels[s.status],
      s.subStatus ? subStatusLabels[s.subStatus] : "",
      s.lastResponseAt ? s.lastResponseAt.slice(0, 10) : "",
      s.hasNote ? "예" : "아니요",
      helpCount(s) ?? "",
    ]
      .map(cell)
      .join(","),
  );
  const blob = new Blob(["﻿" + [header.join(","), ...lines].join("\r\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}
