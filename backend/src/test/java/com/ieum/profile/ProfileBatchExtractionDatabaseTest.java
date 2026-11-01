// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 MySQL에서 누락 대상 조회와 초기 추출 저장을 확인한다.
 * IEUM_PROFILE_DB_TEST=true일 때만 실행하며, 자신의 모의 수료생만 만들고 삭제한다.
 * 기존 시드와 Gemini를 사용하지 않으며 전체 대상 관리 API도 실행하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
class ProfileBatchExtractionDatabaseTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProfileMapper mapper;
    @Autowired private ProfileExtractionWorker worker;
    @Autowired private ObjectMapper json;
    @MockitoBean(enforceOverride = true) private AiClient ai;

    private final List<Long> studentIds = new ArrayList<>();
    private Long courseId;

    @BeforeEach
    void createOwnMockCourse() {
        courseId = insert("""
                INSERT INTO course (name, cohort, campus, started_on, ended_on)
                VALUES (?, '검증', '모의 캠퍼스', '2026-03-01', '2026-08-31')
                """, "모의 초기 추출 검증 과정 " + UUID.randomUUID());
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        for (Long studentId : studentIds) {
            jdbc.update("DELETE FROM student WHERE id = ?", studentId);
        }
        if (courseId != null) jdbc.update("DELETE FROM course WHERE id = ?", courseId);
    }

    @Test
    void selectsMissingProfilesInIdOrderAndExcludesExistingProfiles() {
        Long missingFirst = newStudent();
        Long existing = newStudent();
        Long missingLast = newStudent();
        mapper.upsertCompetencyProfile(existing, expectedProfile(), "0".repeat(64));

        List<Long> targets = mapper.findStudentIdsWithoutCompetencyProfile();

        assertTrue(targets.contains(missingFirst));
        assertTrue(targets.contains(missingLast));
        assertFalse(targets.contains(existing));
        assertEquals(targets.stream().sorted().toList(), targets);
    }

    @Test
    void storesProfileWithHashAndSkipsSecondExtractionWithoutChangingTimestamp() throws Exception {
        Long studentId = newStudent();
        JsonNode expected = expectedProfile();
        when(ai.extractProfile(any())).thenReturn(json.createObjectNode().set("profile", expected));

        assertTrue(worker.extractIfMissing(studentId));

        JsonNode stored = json.readTree(jdbc.queryForObject(
                "SELECT profile FROM competency_profile WHERE student_id = ?", String.class, studentId));
        String hash = jdbc.queryForObject(
                "SELECT source_hash FROM competency_profile WHERE student_id = ?", String.class, studentId);
        LocalDateTime extractedAt = jdbc.queryForObject(
                "SELECT extracted_at FROM competency_profile WHERE student_id = ?", LocalDateTime.class, studentId);
        assertEquals(expected, stored);
        assertTrue(hash != null && hash.matches("[0-9a-f]{64}"));
        assertNotNull(extractedAt);
        assertFalse(mapper.findStudentIdsWithoutCompetencyProfile().contains(studentId));

        assertFalse(worker.extractIfMissing(studentId));

        verify(ai, times(1)).extractProfile(any());
        assertEquals(extractedAt, jdbc.queryForObject(
                "SELECT extracted_at FROM competency_profile WHERE student_id = ?", LocalDateTime.class, studentId));
        assertEquals(hash, jdbc.queryForObject(
                "SELECT source_hash FROM competency_profile WHERE student_id = ?", String.class, studentId));
        assertEquals(expected, json.readTree(jdbc.queryForObject(
                "SELECT profile FROM competency_profile WHERE student_id = ?", String.class, studentId)));
    }

    @Test
    void failedStudentRemainsMissingAndNextStudentCanCommitSuccessfully() {
        Long failed = newStudent();
        Long next = newStudent();
        when(ai.extractProfile(any()))
                .thenThrow(new IllegalStateException("모의 AI 장애"))
                .thenReturn(json.createObjectNode().set("profile", expectedProfile()));

        assertThrows(IllegalStateException.class, () -> worker.extractIfMissing(failed));
        assertTrue(worker.extractIfMissing(next));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM competency_profile WHERE student_id = ?", Integer.class, failed));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM competency_profile WHERE student_id = ?", Integer.class, next));
        List<Long> remaining = mapper.findStudentIdsWithoutCompetencyProfile();
        assertTrue(remaining.contains(failed));
        assertFalse(remaining.contains(next));
        verify(ai, times(2)).extractProfile(any());
    }

    private Long newStudent() {
        Long studentId = insert("""
                INSERT INTO student (course_id, name, desired_job, preferred_region, skills, completed_on)
                VALUES (?, '모의 초기 추출 수료생', '백엔드 개발', '서울', '["Java"]', '2026-08-31')
                """, courseId);
        studentIds.add(studentId);
        return studentId;
    }

    private ObjectNode expectedProfile() {
        ObjectNode profile = json.createObjectNode();
        profile.putArray("roles").add("백엔드 개발");
        profile.putArray("skills");
        profile.putArray("achievements");
        profile.putArray("certificates");
        profile.put("desiredJob", "백엔드 개발");
        profile.put("preferredRegion", "서울");
        return profile;
    }

    private Long insert(String sql, Object... values) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        assertNotNull(key.getKey());
        return key.getKey().longValue();
    }
}
