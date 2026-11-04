"use client";

import Image from "next/image";
import { useState } from "react";
import StudentIcon from "./StudentIcon";

const images = ["/images/career/backend-development.jpg", "/images/career/data-development-v1.png", "/images/career/frontend-development-v1.png"];

/** 회사가 제공한 사진이 아닌 공통 직무 이미지. 이미지를 못 읽어도 카드의 공간과 링크를 유지한다. */
export default function CareerImage({ variant = 0, hero = false, jobTitle = "" }: { variant?: number; hero?: boolean; jobTitle?: string }) {
  const isBackend = /백\s*엔드|\bback[\s-]?end\b/i.test(jobTitle);
  const isFrontend = /프론트\s*엔드|\bfront[\s-]?end\b/i.test(jobTitle);
  const src = !hero ? images[Math.abs(variant) % images.length]
    : isBackend ? images[0]
    : isFrontend ? images[2]
    : "/images/career/career-editorial.webp";
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  return <div className={`s-career-image${hero ? " s-career-image-hero" : ""}`} data-variant={Math.abs(variant) % images.length}>
    {failedSrc === src
      ? <span className="s-image-fallback" aria-hidden="true"><StudentIcon name="briefcase" /></span>
      : <Image src={src} fill sizes={hero ? "(max-width: 639px) 100vw, 55vw" : "(max-width: 639px) 100vw, (max-width: 1023px) 50vw, 33vw"} alt="" loading={hero ? "eager" : "lazy"} onError={() => setFailedSrc(src)} />}
  </div>;
}
