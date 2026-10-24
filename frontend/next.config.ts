import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // cacheComponents 는 끔: 동적 라우트 페이지가 params 를 바로 읽을 수 있게 (Suspense 강제 없음)
  reactCompiler: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
