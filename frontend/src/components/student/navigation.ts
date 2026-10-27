export type SearchQuery = Record<string, string | string[] | undefined>;

export function studentIdFrom(query: SearchQuery) {
  const raw = (Array.isArray(query.studentId) ? query.studentId[0] : query.studentId) ?? process.env.NEXT_PUBLIC_DEMO_STUDENT_ID ?? "1";
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : 1;
}

export function studentHref(path: string, studentId: number) {
  return `${path}?studentId=${studentId}`;
}

export function deadlineLabel(dDay: number | null) {
  if (dDay === null) return "마감일 미정";
  if (dDay < 0) return "마감";
  if (dDay === 0) return "오늘 마감";
  return `마감 D-${dDay}`;
}

export function dateLabel(value: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}
