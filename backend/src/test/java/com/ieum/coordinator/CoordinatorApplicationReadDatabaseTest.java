// 주인: BE-B
package com.ieum.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 지원서 최대 20건을 SELECT로만 확인한다. 테스트 행·상담 기록·상태를 만들거나 변경하지 않는다.
 * 원문은 메모리에서만 비교하고 검증 실패에도 출력하지 않는다. 실제 검토 쓰기 흐름을 검증하는 테스트는 아니다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_COORDINATOR_REVIEW_READ_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never", "app.ai.mock=true", "debug=false",
        "app.seoul.sync-cron=-", "app.status.recalculate-cron=-"
})
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class CoordinatorApplicationReadDatabaseTest {

    private static final Set<String> COMPANY_STATUSES = Set.of("FOUND", "PARTIAL", "NOT_FOUND");
    private static final List<Table> TABLES = List.of(
            new Table("course", "id"), new Table("student", "id"),
            new Table("student_education", "id"), new Table("student_career", "id"),
            new Table("student_certificate", "id"), new Table("student_project", "id"),
            new Table("student_cover_letter", "student_id"), new Table("competency_profile", "student_id"),
            new Table("job_posting", "id"), new Table("company_info", "id"),
            new Table("recommendation", "id"), new Table("recommendation_item", "id"),
            new Table("help_request", "id"), new Table("application", "id"),
            new Table("application_document", "application_id"), new Table("student_status", "student_id"),
            new Table("contact_log", "id"));

    @Autowired private CoordinatorApplicationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void existingReviewSnapshotsMatchTheReadServiceAndAllStoredRowsAndTimesRemainUnchanged() throws Exception {
        Map<String, String> before = fingerprints();
        try {
            List<StoredReview> rows = jdbc.query("""
                    SELECT a.id, a.student_id, a.job_posting_id, a.status,
                           s.name AS student_name, j.company_name, j.title,
                           CAST(ad.document AS CHAR) AS document_json,
                           s.last_response_at, ad.updated_at AS document_updated_at
                    FROM application a
                    JOIN student s ON s.id = a.student_id
                    JOIN job_posting j ON j.id = a.job_posting_id
                    LEFT JOIN application_document ad ON ad.application_id = a.id
                    ORDER BY a.id ASC
                    LIMIT 20
                    """, (row, index) -> new StoredReview(row.getLong("id"), row.getLong("student_id"),
                    row.getLong("job_posting_id"), row.getString("status"), row.getString("student_name"),
                    row.getString("company_name"), row.getString("title"), row.getString("document_json"),
                    time(row, "last_response_at"), time(row, "document_updated_at")));
            if (rows.isEmpty()) {
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM application", Long.class),
                        "기존 지원서가 있다면 소유 학생과 공고도 조회되어야 한다");
                BusinessException failure = assertThrows(BusinessException.class,
                        () -> service.detail(Long.MAX_VALUE));
                assertEquals(ErrorCode.APPLICATION_NOT_FOUND, failure.getErrorCode());
            }
            for (StoredReview stored : rows) verifyStoredReview(stored);
        } finally {
            assertTrue(before.equals(fingerprints()), "기존 17개 테이블의 모든 행과 저장 시각을 보존해야 한다");
        }
    }

    private void verifyStoredReview(StoredReview stored) throws Exception {
        JsonNode original = stored.documentJson() == null ? null : json.readTree(stored.documentJson());
        boolean hidden = "GENERATING".equals(stored.status()) || "FAILED".equals(stored.status());
        JsonNode expected = hidden || original == null || original.isNull() ? null : original;
        if (expected != null && !expected.isObject()) {
            BusinessException failure = assertThrows(BusinessException.class, () -> service.detail(stored.id()));
            assertEquals(ErrorCode.INTERNAL_ERROR, failure.getErrorCode());
        } else {
            CoordinatorApplicationResponse actual = service.detail(stored.id());
            assertTrue(Objects.equals(stored.id(), actual.applicationId()), "저장된 지원서 번호를 유지해야 한다");
            assertTrue(Objects.equals(stored.status(), actual.status()), "저장된 상태를 유지해야 한다");
            assertTrue(Objects.equals(stored.studentId(), actual.student().studentId()),
                    "지원서에 연결된 수료생을 반환해야 한다");
            protectedText(stored.studentName(), actual.student().name());
            assertTrue(Objects.equals(stored.jobPostingId(), actual.job().jobPostingId()),
                    "지원서에 연결된 공고를 반환해야 한다");
            protectedText(stored.companyName(), actual.job().companyName());
            protectedText(stored.title(), actual.job().title());
            assertTrue(Objects.equals(expected, actual.document()),
                    "등록 프로필로 다시 만들지 않고 저장 문서 또는 상태에 따른 null을 반환해야 한다");
            verifySummaryBoundsAndStoredMetadata(expected, actual.verification());
        }
        TimeSnapshot after = jdbc.queryForObject("""
                SELECT s.last_response_at, ad.updated_at AS document_updated_at
                FROM application a
                JOIN student s ON s.id = a.student_id
                LEFT JOIN application_document ad ON ad.application_id = a.id
                WHERE a.id = ?
                """, (row, index) -> new TimeSnapshot(time(row, "last_response_at"),
                time(row, "document_updated_at")), stored.id());
        assertNotNull(after);
        assertTrue(Objects.equals(stored.lastResponseAt(), after.lastResponseAt()),
                "조회로 수료생 마지막 응답 시각을 바꾸면 안 된다");
        assertTrue(Objects.equals(stored.documentUpdatedAt(), after.documentUpdatedAt()),
                "조회로 서류 저장 시각을 바꾸면 안 된다");
    }

    private static void verifySummaryBoundsAndStoredMetadata(JsonNode document,
                                                             CoordinatorApplicationResponse.Verification summary) {
        assertNotNull(summary);
        assertTrue(summary.aiSentenceCount() >= 0);
        assertTrue(summary.aiSentenceWithEvidence() >= 0
                && summary.aiSentenceWithEvidence() <= summary.aiSentenceCount());
        assertTrue(summary.excludedSentenceCount() >= 0);
        assertTrue(summary.userEditedCount() >= 0);
        assertTrue(COMPANY_STATUSES.contains(summary.companyInfoStatus()));
        if (document != null) {
            JsonNode company = document.path("companyInfoStatus");
            if (company.isTextual() && COMPANY_STATUSES.contains(company.textValue())) {
                protectedText(company.textValue(), summary.companyInfoStatus());
            }
            JsonNode excluded = document.path("excludedSentenceCount");
            if (excluded.isIntegralNumber() && excluded.canConvertToInt() && excluded.intValue() >= 0) {
                assertEquals(excluded.intValue(), summary.excludedSentenceCount());
            }
        }
    }

    private Map<String, String> fingerprints() throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (Table table : TABLES) {
            // 테이블·정렬 키는 고정된 코드 목록이며 행 값은 출력하지 않는다.
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + table.name() + " ORDER BY " + table.key());
            byte[] content = json.writeValueAsBytes(rows);
            result.put(table.name(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        }
        return result;
    }

    private static LocalDateTime time(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static void protectedText(String expected, String actual) {
        assertTrue(Objects.equals(expected, actual), "저장된 원문과 null 여부가 같아야 한다");
    }

    private record Table(String name, String key) {}
    private record TimeSnapshot(LocalDateTime lastResponseAt, LocalDateTime documentUpdatedAt) {}
    private record StoredReview(long id, long studentId, long jobPostingId, String status, String studentName,
                                String companyName, String title, String documentJson,
                                LocalDateTime lastResponseAt, LocalDateTime documentUpdatedAt) {}
}
