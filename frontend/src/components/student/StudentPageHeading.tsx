import Link from "next/link";
import StudentIcon from "./StudentIcon";

export default function StudentPageHeading({ eyebrow, title, description, back }: { eyebrow: string; title: string; description?: string; back?: string }) {
  return <>
    {back && <Link className="s-back-link" href={back}><StudentIcon name="chevron" />돌아가기</Link>}
    <header className="s-page-heading"><div><p className="s-eyebrow">{eyebrow}</p><h1>{title}</h1>{description && <p className="s-heading-description">{description}</p>}</div></header>
  </>;
}
