// 주인: FE-A — 루트 레이아웃 (글꼴 · 전역 스타일)
import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";
import "@/styles/font-extended.css";

const notoSansKr = localFont({
  src: "../../public/fonts/NotoSansKR-common.woff2",
  variable: "--font-noto-sans-kr",
  weight: "100 900",
  display: "swap",
  fallback: ["Noto Sans KR Extended", "Arial", "sans-serif"],
  adjustFontFallback: false,
});

export const metadata: Metadata = {
  title: "이음",
  description: "보고하지 않아도, 기회가 먼저 찾아온다 — 수료생 사후관리 AI 에이전트",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className={`${notoSansKr.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
