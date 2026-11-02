export default function StudentHeroArt() {
  return <svg className="s-hero-art" viewBox="0 0 230 250" fill="none" aria-hidden="true">
    <path className="s-art-arch" d="M49 249V115a77 77 0 0 1 154 0v134" />
    <path className="s-art-inner" d="M71 249V117a55 55 0 0 1 110 0v132" />
    <g transform="rotate(-11 118 148)"><rect className="s-art-shadow" x="73" y="97" width="101" height="133" rx="11" /><rect className="s-art-paper" x="61" y="86" width="101" height="133" rx="11" /><path className="s-art-lines" d="M75 112h50M75 129h65M75 140h43" strokeWidth="4" strokeLinecap="round" /><circle className="s-art-inner" cx="112" cy="179" r="20" /><path className="s-art-stem" d="m102 179 7 7 14-15" strokeWidth="3" strokeLinecap="round" /></g>
    <path className="s-art-stem" d="M185 240v-75" strokeWidth="3" /><path className="s-art-leaf" d="M185 204c-32 0-36-25-36-25 30 0 36 25 36 25Z" /><path className="s-art-leaf-dark" d="M185 184c0-29 24-38 24-38 3 30-24 38-24 38Z" />
  </svg>;
}
