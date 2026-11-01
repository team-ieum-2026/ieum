"use client";

import { useEffect, useMemo, useState, useSyncExternalStore, type CSSProperties, type ReactNode } from "react";
import "@/styles/recommendation-tuner.css";
import "@/styles/recommendation-polish.css";

const controls = [
  { key: "scale", label: "전체 크기 · 글씨", min: 70, max: 140 },
  { key: "title", label: "인사 제목 크기", min: 70, max: 150 },
  { key: "width", label: "본문 가로폭", min: 60, max: 100 },
  { key: "hero", label: "상단 배너 높이", min: 70, max: 160 },
  { key: "image", label: "배너 그림 크기", min: 50, max: 150 },
  { key: "padding", label: "공고 카드 내부 여백", min: 50, max: 160 },
  { key: "gap", label: "카드 사이 간격", min: 50, max: 180 },
] as const;
type Tune = Record<(typeof controls)[number]["key"], number>;
const defaults: Tune = { scale: 100, title: 100, width: 100, hero: 100, image: 100, padding: 100, gap: 100 };
const storageKey = "ieum-recommendation-tune-v1";
const tuneChanged = "ieum:recommendation-tune-changed";
const defaultSnapshot = JSON.stringify(defaults);

function readTuneSnapshot(): string {
  const fromUrl = new URL(window.location.href).searchParams.get("recTune");
  if (fromUrl) return fromUrl;
  try { return localStorage.getItem(storageKey) ?? defaultSnapshot; }
  catch { return defaultSnapshot; }
}

function subscribeTune(listener: () => void) {
  window.addEventListener(tuneChanged, listener);
  window.addEventListener("storage", listener);
  window.addEventListener("popstate", listener);
  return () => {
    window.removeEventListener(tuneChanged, listener);
    window.removeEventListener("storage", listener);
    window.removeEventListener("popstate", listener);
  };
}

function writeTune(tune: Tune) {
  const serialized = JSON.stringify(tune);
  try { localStorage.setItem(storageKey, serialized); } catch { /* URL sharing remains available. */ }
  const url = new URL(window.location.href);
  url.searchParams.set("recTune", serialized);
  url.searchParams.delete("recStyle");
  window.history.replaceState(window.history.state, "", url);
  window.dispatchEvent(new Event(tuneChanged));
}

function parseTune(raw: string | null): Tune {
  if (!raw) return { ...defaults };
  try {
    const value = JSON.parse(raw);
    return Object.fromEntries(controls.map(({ key, min, max }) => [key,
      typeof value?.[key] === "number" && Number.isFinite(value[key])
        ? Math.round(Math.min(max, Math.max(min, value[key]))) : defaults[key],
    ])) as Tune;
  } catch { return { ...defaults }; }
}

export default function RecommendationTuner({ children }: { children: ReactNode }) {
  const snapshot = useSyncExternalStore(subscribeTune, readTuneSnapshot, () => defaultSnapshot);
  const tune = useMemo(() => parseTune(snapshot), [snapshot]);
  const [open, setOpen] = useState(false);
  const [share, setShare] = useState("");
  const [message, setMessage] = useState("");

  // Read the external value on mount so SSR defaults cannot overwrite saved preferences.
  useEffect(() => { writeTune(parseTune(readTuneSnapshot())); }, []);

  function update(key: keyof Tune, value: number) {
    writeTune({ ...tune, [key]: value });
    setShare(""); setMessage("");
  }

  async function copySettings() {
    const url = new URL(window.location.href);
    url.searchParams.set("recTune", JSON.stringify(tune));
    url.searchParams.delete("recStyle");
    const text = ["이음 맞춤 공고 화면 비율", ...controls.map(({ key, label }) => `${label}: ${tune[key]}%`),
      `화면 크기: ${window.innerWidth} × ${window.innerHeight}`, `설정 링크: ${url.href}`].join("\n");
    setShare(text);
    try { await navigator.clipboard.writeText(text); setMessage("복사했어요. 채팅에 붙여넣어 보내주세요."); }
    catch { setMessage("아래 설정 내용을 선택해 복사해주세요."); }
  }

  const style = Object.fromEntries(Object.entries(tune).map(([key, value]) => [`--rec-${key}`, value / 100])) as CSSProperties;
  return <>
    <div className="s-recommend-content s-recommend-compact" style={style}>{children}</div>
    <div className="s-tuner">
      <button type="button" className="s-tuner-launch" aria-expanded={open} aria-controls="recommendation-tuner" onClick={() => setOpen(value => !value)}>{open ? "조절 패널 닫기" : "화면 비율 조절"}</button>
      {open && <section id="recommendation-tuner" className="s-tuner-panel" aria-label="맞춤 공고 화면 비율 조절">
        <h2>맞춤 공고 비율 조절</h2>
        <p>100%가 기본 크기예요. 움직이면 바로 반영돼요.</p>
        {controls.map(({ key, label, min, max }) => <label className="s-tuner-control" key={key}>
          <span>{label}<output htmlFor={`rec-${key}`}>{tune[key]}%</output></span>
          <input id={`rec-${key}`} type="range" min={min} max={max} step="1" value={tune[key]} onChange={event => update(key, Number(event.target.value))} />
        </label>)}
        <div className="s-tuner-actions"><button type="button" onClick={() => { writeTune({ ...defaults }); setShare(""); setMessage("기본 비율로 되돌렸어요."); }}>기본값 복원</button><button type="button" onClick={copySettings}>설정 복사</button></div>
        <p role="status">{message || "이 브라우저에 자동 저장돼요. 설정을 복사해 보내주세요."}</p>
        {share && <textarea aria-label="복사할 비율 설정" readOnly value={share} onFocus={event => event.target.select()} rows={5} />}
      </section>}
    </div>
  </>;
}
