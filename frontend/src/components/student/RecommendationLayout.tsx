import type { ReactNode } from "react";
import "@/styles/recommendation-layout.css";
import "@/styles/recommendation-polish.css";

export default function RecommendationLayout({ children }: { children: ReactNode }) {
  return <div className="s-recommend-content s-recommend-compact">{children}</div>;
}
