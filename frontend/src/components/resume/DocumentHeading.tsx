import type { JobBrief } from "@/types/resume";

export default function DocumentHeading({ title, job, name }: { title: string; job: JobBrief; name?: string | null }) {
  return <header className="s-student-document-heading">
    <p className="s-student-document-company">{job.companyName}</p>
    <h2>{title}{name && <><span className="s-student-document-divider" aria-hidden="true">·</span><span>{name}</span></>}</h2>
    <p className="s-student-document-job">{job.title}</p>
  </header>;
}
