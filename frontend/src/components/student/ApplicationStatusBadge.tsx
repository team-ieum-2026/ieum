import type { ApplicationStatus } from "@/types/student";
import { applicationLabels } from "./labels";

export default function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  return <span className={`s-status s-status-${status}`}>{applicationLabels[status]}</span>;
}
