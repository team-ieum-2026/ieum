// 주인: FE-B — C4 서류 검토 · 승인 (FE-B-08)
import ApplicationReviewScreen from "@/components/coordinator/ApplicationReviewScreen";

export default async function ApplicationReviewPage({
  params,
}: PageProps<"/coordinator/applications/[applicationId]">) {
  const { applicationId } = await params;
  return <ApplicationReviewScreen key={applicationId} applicationId={Number(applicationId)} />;
}
