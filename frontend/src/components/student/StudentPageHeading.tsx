import Link from "next/link";
import type { ReactNode } from "react";
import StudentIcon from "./StudentIcon";

export default function StudentPageHeading({ title, description, back }: { title: string; description?: ReactNode; back?: string }) {
  return <>
    {back && <Link className="s-back-link" href={back}><StudentIcon name="chevron" />돌아가기</Link>}
    <header className="s-page-heading"><div><h1>{title}</h1>{description && <p className="s-heading-description">{description}</p>}</div></header>
  </>;
}
