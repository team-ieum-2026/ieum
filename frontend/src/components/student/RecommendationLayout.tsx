import type { ReactNode } from "react";

export default function RecommendationLayout({ children }: { children: ReactNode }) {
  return <div className="s-recommend-content">{children}</div>;
}
