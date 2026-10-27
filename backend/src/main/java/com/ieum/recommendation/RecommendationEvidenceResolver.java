// 주인: BE-A
package com.ieum.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.ieum.profile.StudentProfileResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 추천 근거 { refType, refId } 에 화면용 label · quote 를 채운다 (M2).
 * 원문은 BE-B 프로필 조회 결과에서만 읽고, 찾을 수 없는 근거는 버린다.
 */
final class RecommendationEvidenceResolver {

    private static final int COVER_LETTER_QUOTE_MAX = 80;

    private static final Map<String, String> DEGREE = Map.of(
            "HIGH_SCHOOL", "고등학교", "ASSOCIATE", "전문학사", "BACHELOR", "학사",
            "MASTER", "석사", "DOCTOR", "박사");
    private static final Map<String, String> EDUCATION_STATUS = Map.of(
            "GRADUATED", "졸업", "EXPECTED", "졸업 예정", "ENROLLED", "재학", "DROPPED", "중퇴");

    private final StudentProfileResponse profile;

    RecommendationEvidenceResolver(StudentProfileResponse profile) {
        this.profile = profile;
    }

    /** reasons JSON [{ text, evidence: [{ refType, refId }] }] → 화면용 근거 */
    List<RecommendationItemDetailResponse.Reason> reasons(JsonNode reasons) {
        List<RecommendationItemDetailResponse.Reason> result = new ArrayList<>();
        for (JsonNode r : reasons) {
            String text = r.path("text").asText("");
            if (text.isBlank()) {
                continue;
            }
            List<RecommendationItemDetailResponse.Evidence> evidence = new ArrayList<>();
            for (JsonNode e : r.path("evidence")) {
                RecommendationItemDetailResponse.Evidence resolved =
                        resolve(e.path("refType").asText(""), e.path("refId").asLong(-1));
                if (resolved != null) {
                    evidence.add(resolved);
                }
            }
            result.add(new RecommendationItemDetailResponse.Reason(text, evidence));
        }
        return result;
    }

    private RecommendationItemDetailResponse.Evidence resolve(String refType, long refId) {
        return switch (refType) {
            case "PROJECT" -> find(profile.projects(), p -> p.itemId(), refId)
                    .map(p -> evidence(refType, refId, p.title() + " · 프로젝트",
                            join(techStack(p.techStack()), p.periodText(),
                                    p.teamSize() == null ? null : p.teamSize() + "명")))
                    .orElse(null);
            case "CERTIFICATE" -> find(profile.certificates(), c -> c.itemId(), refId)
                    .map(c -> evidence(refType, refId, c.name() + " · 자격증",
                            join(c.issuer(), c.acquiredOn() == null ? null : c.acquiredOn() + " 취득")))
                    .orElse(null);
            case "EDUCATION" -> find(profile.education(), e -> e.itemId(), refId)
                    .map(e -> evidence(refType, refId, e.schoolName() + " · 학력",
                            join(e.major(), DEGREE.get(e.degree()), EDUCATION_STATUS.get(e.status()))))
                    .orElse(null);
            case "CAREER" -> find(profile.careers(), c -> c.itemId(), refId)
                    .map(c -> evidence(refType, refId, c.companyName() + " · 경력",
                            join(c.position(), period(c.startedOn(), c.endedOn()))))
                    .orElse(null);
            case "COURSE" -> profile.course() != null && Objects.equals(profile.course().courseId(), refId)
                    ? evidence(refType, refId, profile.course().name() + " · 교육 과정",
                            join(profile.course().campus(),
                                    period(profile.course().startedOn(), profile.course().endedOn())))
                    : null;
            case "COVER_LETTER" -> profile.coverLetter() != null && profile.coverLetter().content() != null
                    && !profile.coverLetter().content().isBlank()
                    ? evidence(refType, refId, "등록 자기소개서", shorten(profile.coverLetter().content()))
                    : null;
            default -> null;
        };
    }

    private static <T> Optional<T> find(List<T> items, Function<T, Long> id, long refId) {
        return items == null ? Optional.empty()
                : items.stream().filter(i -> Objects.equals(id.apply(i), refId)).findFirst();
    }

    private static RecommendationItemDetailResponse.Evidence evidence(String refType, long refId, String label,
                                                                        String quote) {
        return new RecommendationItemDetailResponse.Evidence(refType, refId, label, quote, null, null);
    }

    private static String techStack(List<String> techStack) {
        return techStack == null || techStack.isEmpty() ? null : String.join(", ", techStack);
    }

    private static String period(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (from == null ? "" : from.toString()) + " ~ " + (to == null ? "" : to.toString());
    }

    private static String shorten(String content) {
        String oneLine = content.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= COVER_LETTER_QUOTE_MAX ? oneLine : oneLine.substring(0, COVER_LETTER_QUOTE_MAX) + "…";
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" · "));
    }
}
