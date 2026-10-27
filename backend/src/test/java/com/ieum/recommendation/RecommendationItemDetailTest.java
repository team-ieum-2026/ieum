package com.ieum.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.profile.StudentProfileResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationItemDetailTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void summaryTakesFirstTwoContentLinesWithoutHeadersOrLinks() {
        String description = """
                [직무] 웹 개발자
                [내용] [모집직무]
                - AI 연계 웹 서비스 시스템 구축 및 개발, 관리

                https://example.com/careers
                * 자사 서비스 및 자사 연계 웹 서비스 시스템 구축 및 개발, 관리
                - 세 번째 줄은 쓰지 않음
                [근무 시간] 평일 9시 ~ 6시""";

        assertEquals(List.of("AI 연계 웹 서비스 시스템 구축 및 개발, 관리", "자사 서비스 및 자사 연계 웹 서비스 시스템 구축 및 개발, 관리"),
                RecommendationItemService.summary(description));
    }

    @Test
    void summaryCutsLongLinesAndStopsAtNextLabel() {
        String longLine = "가".repeat(70);
        List<String> summary = RecommendationItemService.summary("[내용] " + longLine + "\n[전형] 서류, 면접");

        assertEquals(List.of("가".repeat(60) + "…"), summary);
        assertEquals(List.of(), RecommendationItemService.summary(null));
    }

    @Test
    void evidenceGetsLabelAndQuoteFromProfileAndUnknownRefsAreDropped() throws Exception {
        var profile = new StudentProfileResponse(
                1L, null, null,
                new StudentProfileResponse.Course(1L, "AI Agent 과정", "1기", "동대문캠퍼스",
                        LocalDate.of(2026, 3, 2), LocalDate.of(2026, 8, 29)),
                List.of(), List.of(),
                List.of(new StudentProfileResponse.Certificate(2L, "REGISTERED", "SQLD", "한국데이터산업진흥원",
                        LocalDate.of(2026, 9, 1))),
                List.of(),
                List.of(new StudentProfileResponse.Project(11L, "모아요", "6주", 4, List.of("Java", "Spring Boot"),
                        null, true)),
                null, null);
        var reasons = json.readTree("""
                [ { "text": "Spring Boot 경험이 맞아요", "evidence": [
                      { "refType": "PROJECT", "refId": 11 }, { "refType": "PROJECT", "refId": 99 } ] },
                  { "text": "SQLD가 우대사항이에요", "evidence": [ { "refType": "CERTIFICATE", "refId": 2 } ] },
                  { "text": "교육 과정에서 React를 배웠어요", "evidence": [ { "refType": "COURSE", "refId": 1 } ] },
                  { "text": "희망 직무가 맞아요", "evidence": [] } ]""");

        var resolved = new RecommendationEvidenceResolver(profile).reasons(reasons);

        assertEquals(4, resolved.size());
        var project = resolved.get(0).evidence();
        assertEquals(1, project.size());
        assertEquals("모아요 · 프로젝트", project.get(0).label());
        assertEquals("Java, Spring Boot · 6주 · 4명", project.get(0).quote());
        assertEquals("한국데이터산업진흥원 · 2026-09-01 취득", resolved.get(1).evidence().get(0).quote());
        assertEquals("AI Agent 과정 · 교육 과정", resolved.get(2).evidence().get(0).label());
        assertTrue(resolved.get(3).evidence().isEmpty());
    }
}
