import type { ReactNode } from "react";

const paths: Record<string, ReactNode> = {
  logout: <><rect x="1" y="1" width="22" height="22" rx="6" fill="var(--color-surface)" stroke="none" /><path d="M10 8V6h8v12h-8v-2" stroke="currentColor" strokeWidth="1.8" /><path d="m4 12 4-4v2.5h5v3H8V16Z" fill="currentColor" stroke="none" /></>,
  more: <path d="M4 6h16M4 12h16M4 18h16" />,
  sprout: <path d="M12 21v-8M12 15C4 15 3 10 4 6c6 0 9 3 8 9ZM12 12c0-6 3-9 8-9 1 6-2 10-8 10" />,
  cap: <path d="m2 9 10-5 10 5-10 5L2 9Zm4 2v6c4 3 8 3 12 0v-6m4-2v7" />,
  people: <><circle cx="9" cy="8" r="3" /><path d="M3 21v-3a6 6 0 0 1 12 0v3m2-16a3 3 0 0 1 0 6m1 3a5 5 0 0 1 4 5v2" /></>,
  help: <><circle cx="12" cy="12" r="9" /><path d="M9.5 9a2.5 2.5 0 1 1 4 2c-1 .7-1.5 1-1.5 2m0 3h.01" /></>,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  sparkles: <><path d="m12 3 2.3 6.7L21 12l-6.7 2.3L12 21l-2.3-6.7L3 12l6.7-2.3L12 3Z" /><path d="M20 2v4m-2-2h4" /></>,
  folder: <><path d="M3 7V5a1 1 0 0 1 1-1h5l3 3h8a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7Z" /><path d="M3 10h18" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></>,
  message: <><path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2V11.5a9.5 9.5 0 0 1 19 0Z" /><path d="M7 10h9m-9 4h6" /></>,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  chevron: <path d="m9 5 7 7-7 7" />,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 11h18" /></>,
  code: <path d="m8 7-5 5 5 5m8-10 5 5-5 5m-3-14-2 18" />,
  check: <path d="m5 12 4 4L19 6" />,
  shield: <><path d="M12 2 3 6v6c0 6 9 10 9 10s9-4 9-10V6l-9-4Z" /><path d="m8 12 3 3 5-6" /></>,
  refresh: <><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6 6a8 8 0 0 1 13 2l1 4M4 12l1 4a8 8 0 0 0 13 2" /></>,
  pin: <><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z" /><circle cx="12" cy="10" r="2" /></>,
  briefcase: <><rect x="3" y="7" width="18" height="14" rx="2" /><path d="M8 7V4h8v3M3 12a23 23 0 0 0 18 0M10 13h4" /></>,
  file: <><path d="M14 2H5v20h14V7l-5-5Z" /><path d="M14 2v5h5M8 12h8M8 16h6" /></>,
  leaf: <><path d="M20 3C8 2 2 8 5 15s14 7 15-12Z" /><path d="M3 21 15 9" /></>,
  edit: <><path d="m15 4 5 5M4 20l5-1L21 7a2.8 2.8 0 0 0-4-4L5 15l-1 5Z" /></>,
};

export default function StudentIcon({ name, className = "" }: { name: string; className?: string }) {
  return <svg className={`s-icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.55" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.file}</svg>;
}
