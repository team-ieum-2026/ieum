// 주인: BE-B
package com.ieum.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 시드의 원천 데이터와 기대 상태를 따로 읽어 실제 서비스 판정·집계를 검증한다. DB·AI 호출 없음. */
class StatusSeedTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-28", "2027-02-10"})
    void allFiftySeedStudentsMatchExpectedStatusesAndDashboardCountsOnAnyLoadDate(String baseDate)
            throws IOException {
        StatusSeedFixture fixture = StatusSeedFixture.load(LocalDate.parse(baseDate));
        StatusMapper mapper = mock(StatusMapper.class);
        StatusService service = new StatusService(mapper, fixture.clock());
        Map<Long, StatusSeedFixture.Expected> saved = new LinkedHashMap<>();
        when(mapper.upsertStatus(any(), any())).thenAnswer(invocation -> {
            Long studentId = invocation.getArgument(0);
            StatusDecision decision = invocation.getArgument(1);
            saved.put(studentId, new StatusSeedFixture.Expected(decision.status(), decision.subStatus()));
            return 1;
        });

        assertEquals(50, fixture.students.size());
        assertEquals(LongStream.rangeClosed(1, 50).boxed().collect(Collectors.toSet()), fixture.expected.keySet());
        assertEquals(31, fixture.recommendations.size());
        assertEquals(93, fixture.items.size());
        assertEquals(4, fixture.helpRequests.size());
        assertEquals(4, fixture.applications.size());
        assertCounts(fixture.expected);

        for (StatusSeedFixture.Student student : fixture.students) {
            prepareStudent(mapper, fixture, student);
            service.recalculate(student.id());
        }

        assertEquals(fixture.expected, saved, "Every checked-in seed status and subStatus must match");
        assertCounts(saved);
        verify(mapper, times(50)).upsertStatus(any(), any());
        verify(mapper, never()).updateLastResponseAt(any(), any());
    }

    private static void prepareStudent(StatusMapper mapper, StatusSeedFixture fixture,
                                       StatusSeedFixture.Student student) {
        long id = student.id();
        StatusData.Student row = new StatusData.Student();
        row.setStudentId(id);
        row.setLastResponseAt(student.lastResponseAt());
        row.setInactivitySince(student.lastResponseAt() != null
                ? student.lastResponseAt() : student.completedOn().atStartOfDay());
        when(mapper.lockStudent(id)).thenReturn(row);

        List<StatusSeedFixture.Recommendation> recommendations = fixture.recommendations.stream()
                .filter(recommendation -> recommendation.studentId() == id).toList();
        Set<Long> recommendationIds = recommendations.stream().map(StatusSeedFixture.Recommendation::id)
                .collect(Collectors.toSet());
        List<StatusData.Response> responses = fixture.items.stream()
                .filter(item -> recommendationIds.contains(item.recommendationId()))
                .sorted(Comparator.comparing(StatusSeedFixture.Item::respondedAt,
                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(Comparator.comparingLong(StatusSeedFixture.Item::id).reversed()))
                .map(StatusSeedTest::response).toList();
        when(mapper.findResponses(id)).thenReturn(responses);
        when(mapper.findOpenHelpRequestIds(id)).thenReturn(fixture.helpRequests.stream()
                .filter(help -> help.studentId() == id && "OPEN".equals(help.status()))
                .map(help -> 1L).toList());
        when(mapper.findActiveApplicationIds(id)).thenReturn(fixture.applications.stream()
                .filter(application -> application.studentId() == id
                        && Set.of("SUBMITTED", "APPROVED", "DELIVERED").contains(application.status()))
                .map(application -> 1L).toList());

        var latest = recommendations.stream().max(
                Comparator.comparing(StatusSeedFixture.Recommendation::createdAt)
                        .thenComparingLong(StatusSeedFixture.Recommendation::id));
        when(mapper.findLatestRecommendationId(id)).thenReturn(latest.map(StatusSeedFixture.Recommendation::id)
                .orElse(null));
        latest.ifPresent(recommendation -> when(mapper.findRecommendationItemIds(recommendation.id()))
                .thenReturn(fixture.items.stream()
                        .filter(item -> item.recommendationId() == recommendation.id())
                        .map(StatusSeedFixture.Item::id).toList()));
    }

    private static StatusData.Response response(StatusSeedFixture.Item item) {
        StatusData.Response response = new StatusData.Response();
        response.setItemId(item.id());
        response.setResponse(item.response());
        response.setPassReason(item.passReason());
        response.setRespondedAt(item.respondedAt());
        return response;
    }

    private static void assertCounts(Map<Long, StatusSeedFixture.Expected> actual) {
        assertEquals(StatusSeedFixture.STATUS_COUNTS, StatusSeedFixture.statusCounts(actual));
        assertEquals(StatusSeedFixture.SEEKING_COUNTS, StatusSeedFixture.seekingCounts(actual));
    }
}
