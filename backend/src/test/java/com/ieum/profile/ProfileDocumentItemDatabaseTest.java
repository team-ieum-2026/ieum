// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ieum.ai.AiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BE-A가 호출하는 공개 프로필 서비스를 기존 MySQL과 모의 시드로 검증한다.
 * IEUM_PROFILE_DB_TEST=true인 경우에만 실행하며 모든 쓰기는 외부 트랜잭션에서 롤백한다.
 * AUTO_INCREMENT의 번호 공백은 남을 수 있다. 환경 값과 기존 등록 자료는 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true",
        "app.seoul.sync-cron=-",
        "app.status.recalculate-cron=-"
})
class ProfileDocumentItemDatabaseTest {

    private static final Long STUDENT_ID = 1L;
    private static final Long OTHER_STUDENT_ID = 2L;
    private static final List<String> TABLES = List.of("student", "student_education", "student_career",
            "student_certificate", "student_project", "student_cover_letter", "competency_profile");

    @Autowired
    private ProfileService profiles;
    @Autowired
    private ProfileMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean(enforceOverride = true)
    private AiClient aiClient;

    private TransactionTemplate transactions;

    @BeforeEach
    void requireTransactionalTablesAndMockSeed() {
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('student', 'student_education', 'student_career',
                                     'student_certificate', 'student_project',
                                     'student_cover_letter', 'competency_profile')
                """, String.class);
        assertEquals(TABLES.size(), engines.size(), "기존 프로필 스키마가 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase),
                "롤백으로 기존 자료를 보존할 수 있는 테이블에서만 실행한다");
        assertNotNull(mapper.findStudent(STUDENT_ID), "모의 시드 수료생 1이 있어야 한다");
        assertNotNull(mapper.findStudent(OTHER_STUDENT_ID), "모의 시드 수료생 2가 있어야 한다");
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void documentEducationCrudReturnsGeneratedIdAndPreservesOmittedValues() {
        assertDocumentRecordCrud("EDUCATION");
    }

    @Test
    void documentCareerCrudReturnsGeneratedIdAndPreservesOmittedValues() {
        assertDocumentRecordCrud("CAREER");
    }

    @Test
    void documentCertificateCrudReturnsGeneratedIdAndPreservesOmittedValues() {
        assertDocumentRecordCrud("CERTIFICATES");
    }

    @ParameterizedTest
    @CsvSource({
            "EDUCATION,ADD", "EDUCATION,UPDATE", "EDUCATION,DELETE",
            "CAREER,ADD", "CAREER,UPDATE", "CAREER,DELETE",
            "CERTIFICATES,ADD", "CERTIFICATES,UPDATE", "CERTIFICATES,DELETE"
    })
    void documentMutationsDoNotCallAiBeforeCommit(String section, String operation) {
        withRollback(status -> {
            Long itemId = "ADD".equals(operation) ? null : recordFixture(section, STUDENT_ID);
            String changedName = marker();
            List<Map<String, Object>> competencyBefore = competencyRows();
            assertFalse(status.isRollbackOnly());

            {
                if ("ADD".equals(operation)) profiles.addItem(STUDENT_ID, section, validItem(section, changedName));
                else if ("UPDATE".equals(operation)) profiles.updateItem(STUDENT_ID, section, itemId,
                        json.createObjectNode().put(nameField(section), changedName));
                else profiles.deleteItem(STUDENT_ID, section, itemId);
            };

            assertFalse(status.isRollbackOnly(), "Source saves must complete without invoking AI before commit");
            if ("ADD".equals(operation)) {
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + table(section)
                                + " WHERE student_id = ? AND " + nameColumn(section) + " = ?",
                        Long.class, STUDENT_ID, changedName), "INSERT 뒤 AI 실패가 발생해야 한다");
            } else if ("UPDATE".equals(operation)) {
                assertEquals(changedName, recordRow(section, STUDENT_ID, itemId).get(nameColumn(section)),
                        "UPDATE 뒤 AI 실패가 발생해야 한다");
            } else {
                assertTrue(recordRows(section, STUDENT_ID, itemId).isEmpty(), "DELETE 뒤 AI 실패가 발생해야 한다");
            }
            assertTrue(competencyBefore.equals(competencyRows()),
                    "실패한 추출 결과로 기존 역량·해시·추출 시각을 변경하면 안 된다");
        });
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentRecordUpdateAndDeleteCannotTargetAnotherStudent(String section) {
        withRollback(status -> {
            Long foreignId = recordFixture(section, OTHER_STUDENT_ID);
            Map<String, List<Map<String, Object>>> before = snapshot();
            BusinessException updateFailure = assertThrows(BusinessException.class,
                    () -> profiles.updateItem(STUDENT_ID, section, foreignId,
                            json.createObjectNode().put(nameField(section), marker())));
            BusinessException deleteFailure = assertThrows(BusinessException.class,
                    () -> profiles.deleteItem(STUDENT_ID, section, foreignId));
            assertEquals(ErrorCode.INVALID_REQUEST, updateFailure.getErrorCode());
            assertEquals(ErrorCode.INVALID_REQUEST, deleteFailure.getErrorCode());
            assertTrue(before.equals(snapshot()), "다른 수료생의 항목과 두 수료생의 원문·역량을 모두 보존해야 한다");
        });
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDUCATION", "CAREER", "CERTIFICATES"})
    void documentPatchReadsLatestDatabaseValueInsteadOfCachedItem(String section) {
        JsonNode expectedCompetency = existingCompetency();
        String latestOptionalValue = "모의 최신 보관값";
        withRollback(status -> {
            Long itemId = recordFixture(section, STUDENT_ID);
            // MyBatis 세션에 이전 단건 조회가 있어도 JDBC가 바꾼 값을 병합해야 한다.
            readItem(section, itemId);
            jdbc.update("UPDATE " + table(section) + " SET " + optionalColumn(section)
                            + " = ? WHERE student_id = ? AND id = ?",
                    latestOptionalValue, STUDENT_ID, itemId);

            profiles.updateItem(STUDENT_ID, section, itemId,
                    json.createObjectNode().put(nameField(section), marker()));

            assertEquals(latestOptionalValue, recordRow(section, STUDENT_ID, itemId).get(optionalColumn(section)),
                    "누락한 필드는 캐시의 예전 값 대신 현재 DB 값으로 유지해야 한다");
            assertStoredCompetency(expectedCompetency);
            verifyNoInteractions(aiClient);
        });
    }

    @Test
    void documentProjectDeleteRemovesOnlyItsOwnProjectAndKeepsPreviousCompetencyBeforeCommit() {
        JsonNode expectedCompetency = existingCompetency();
        withRollback(status -> {
            ProfileData.Project project = projectFixture();
            List<Map<String, Object>> otherProjects = projectRows(OTHER_STUDENT_ID);
            profiles.deleteItem(STUDENT_ID, "PROJECTS", project.getItemId());
            assertNull(mapper.findProjectItem(STUDENT_ID, project.getItemId()));
            assertTrue(otherProjects.equals(projectRows(OTHER_STUDENT_ID)), "다른 수료생의 프로젝트는 보존해야 한다");
            assertStoredCompetency(expectedCompetency);
            verifyNoInteractions(aiClient);
        });
    }

    @Test
    void documentPortfolioDeleteOnlyExcludesExistingAttachmentAndPreservesOriginals() {
        JsonNode expectedCompetency = existingCompetency();
        withRollback(status -> {
            ProfileData.Project original = projectFixture();
            profiles.deleteItem(STUDENT_ID, "PORTFOLIO", original.getItemId());
            ProfileData.Project changed = mapper.findProjectItem(STUDENT_ID, original.getItemId());
            assertNotNull(changed, "서류의 포트폴리오 삭제로 프로젝트 행을 지우면 안 된다");
            assertFalse(changed.isIncludeInApply());
            assertEquals("USER", changed.getSource());
            assertEquals(original.getTitle(), changed.getTitle());
            assertEquals(original.getPeriodText(), changed.getPeriodText());
            assertEquals(original.getTeamSize(), changed.getTeamSize());
            assertEquals(original.getTechStack(), changed.getTechStack());
            assertEquals(original.getPortfolioFileName(), changed.getPortfolioFileName());
            assertEquals(original.getReadme(), changed.getReadme());
            assertEquals(original.getRoleNote(), changed.getRoleNote());
            assertStoredCompetency(expectedCompetency);
        });
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROJECTS", "PORTFOLIO"})
    void savesDocumentProjectAndPortfolioDeletionWithoutCallingAiBeforeCommit(String section) {
        withRollback(status -> {
            ProfileData.Project original = projectFixture();
            List<Map<String, Object>> competencyBefore = competencyRows();
            assertFalse(status.isRollbackOnly());
            profiles.deleteItem(STUDENT_ID, section, original.getItemId());

            assertFalse(status.isRollbackOnly(), "Source saves must complete without invoking AI before commit");
            ProfileData.Project changed = mapper.findProjectItem(STUDENT_ID, original.getItemId());
            if ("PROJECTS".equals(section)) assertNull(changed, "프로젝트 DELETE 뒤 AI가 실패해야 한다");
            else {
                assertNotNull(changed);
                assertFalse(changed.isIncludeInApply(), "포함 여부 UPDATE 뒤 AI가 실패해야 한다");
                assertEquals(original.getPortfolioFileName(), changed.getPortfolioFileName());
                assertEquals(original.getReadme(), changed.getReadme());
                assertEquals(original.getRoleNote(), changed.getRoleNote());
            }
            assertTrue(competencyBefore.equals(competencyRows()));
        });
        verifyNoInteractions(aiClient);
    }

    private void assertDocumentRecordCrud(String section) {
        JsonNode expectedCompetency = existingCompetency();
        withRollback(status -> {
            String originalName = marker();
            ObjectNode item = validItem(section, originalName).put("studentId", OTHER_STUDENT_ID)
                    .put("itemId", -1).put("source", "REGISTERED");
            Long itemId = profiles.addItem(STUDENT_ID, section, item);
            assertNotNull(itemId, "공개 추가 서비스는 실제 생성 ID를 반환해야 한다");
            assertTrue(itemId > 0);
            Map<String, Object> added = recordRow(section, STUDENT_ID, itemId);
            assertEquals(originalName, added.get(nameColumn(section)));
            assertEquals("USER", added.get("source"));
            String optionalBefore = (String) added.get(optionalColumn(section));
            assertNotNull(optionalBefore);
            assertStoredCompetency(expectedCompetency);
            String addedHash = sourceHash();

            String renamed = marker();
            profiles.updateItem(STUDENT_ID, section, itemId, json.createObjectNode()
                    .put(nameField(section), renamed).put("studentId", OTHER_STUDENT_ID)
                    .put("itemId", -1).put("source", "REGISTERED"));
            Map<String, Object> changed = recordRow(section, STUDENT_ID, itemId);
            assertEquals(renamed, changed.get(nameColumn(section)));
            assertEquals(optionalBefore, changed.get(optionalColumn(section)));
            assertEquals("USER", changed.get("source"));
            assertStoredCompetency(expectedCompetency);
            assertEquals(addedHash, sourceHash(), "Extraction must remain unchanged before the source transaction commits");

            profiles.updateItem(STUDENT_ID, section, itemId,
                    json.createObjectNode().putNull(optionalField(section)));
            Map<String, Object> cleared = recordRow(section, STUDENT_ID, itemId);
            assertEquals(renamed, cleared.get(nameColumn(section)));
            assertNull(cleared.get(optionalColumn(section)), "명시한 null만 선택 값을 비워야 한다");
            assertStoredCompetency(expectedCompetency);
            String clearedHash = sourceHash();

            profiles.deleteItem(STUDENT_ID, section, itemId);
            assertTrue(recordRows(section, STUDENT_ID, itemId).isEmpty());
            assertStoredCompetency(expectedCompetency);
            assertEquals(clearedHash, sourceHash(), "Extraction must remain unchanged before the source transaction commits");
        });
        verifyNoInteractions(aiClient);
    }

    private void withRollback(Consumer<TransactionStatus> test) {
        Map<String, List<Map<String, Object>>> before = snapshot();
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    test.accept(status);
                } finally {
                    // 서비스 프록시의 롤백 표시가 회귀해도 테스트 자료는 커밋하지 않는다.
                    status.setRollbackOnly();
                }
            });
        } finally {
            // 비교 실패 메시지에 등록 자료나 환경 값을 출력하지 않는다.
            assertTrue(before.equals(snapshot()), "롤백 후 두 수료생의 7개 테이블 전체 원문·역량·시각을 보존해야 한다");
        }
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : TABLES) {
            String studentColumn = "student".equals(table) ? "id" : "student_id";
            String orderColumn = "student_cover_letter".equals(table) || "competency_profile".equals(table)
                    ? "student_id" : "id";
            result.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE " + studentColumn
                    + " IN (?, ?) ORDER BY " + orderColumn, STUDENT_ID, OTHER_STUDENT_ID));
        }
        return result;
    }

    private JsonNode existingCompetency() {
        return mapper.findStudent(STUDENT_ID).getCompetencyProfile();
    }

    private void assertStoredCompetency(JsonNode expected) {
        assertEquals(expected, mapper.findCurrentStudent(STUDENT_ID).getCompetencyProfile(),
                "Extraction must remain unchanged before the source transaction commits");
        verifyNoInteractions(aiClient);
    }

    private String sourceHash() {
        List<String> hashes = jdbc.queryForList("SELECT source_hash FROM competency_profile WHERE student_id = ?",
                String.class, STUDENT_ID);
        return hashes.isEmpty() ? null : hashes.get(0);
    }

    private List<Map<String, Object>> competencyRows() {
        return jdbc.queryForList("SELECT * FROM competency_profile WHERE student_id IN (?, ?) ORDER BY student_id",
                STUDENT_ID, OTHER_STUDENT_ID);
    }

    private ObjectNode validItem(String section, String name) {
        ObjectNode item = json.createObjectNode().put(nameField(section), name);
        if ("EDUCATION".equals(section)) item.put("major", "모의 원래 전공").put("degree", "BACHELOR")
                .put("status", "GRADUATED").put("graduatedOn", "2026-02-28");
        else if ("CAREER".equals(section)) item.put("position", "모의 원래 직무")
                .put("startedOn", "2025-03-01").put("endedOn", "2026-02-28").put("description", "모의 경력 설명");
        else item.put("issuer", "모의 원래 발급 기관").put("acquiredOn", "2026-09-01");
        return item;
    }

    private Long recordFixture(String section, Long studentId) {
        assertEquals(studentId, mapper.lockStudent(studentId));
        ObjectNode item = validItem(section, marker());
        if ("EDUCATION".equals(section)) {
            var row = new ProfileData.Education();
            row.setStudentId(studentId);
            row.setSchoolName(item.get("schoolName").textValue());
            row.setMajor(item.get("major").textValue());
            row.setDegree("BACHELOR");
            row.setStatus("GRADUATED");
            row.setGraduatedOn(LocalDate.of(2026, 2, 28));
            assertEquals(1, mapper.insertEducation(row));
            assertNotNull(row.getItemId());
            return row.getItemId();
        } else if ("CAREER".equals(section)) {
            var row = new ProfileData.Career();
            row.setStudentId(studentId);
            row.setCompanyName(item.get("companyName").textValue());
            row.setPosition(item.get("position").textValue());
            row.setStartedOn(LocalDate.of(2025, 3, 1));
            row.setEndedOn(LocalDate.of(2026, 2, 28));
            row.setDescription(item.get("description").textValue());
            assertEquals(1, mapper.insertCareer(row));
            assertNotNull(row.getItemId());
            return row.getItemId();
        } else {
            var row = new ProfileData.Certificate();
            row.setStudentId(studentId);
            row.setName(item.get("name").textValue());
            row.setIssuer(item.get("issuer").textValue());
            row.setAcquiredOn(LocalDate.of(2026, 9, 1));
            assertEquals(1, mapper.insertCertificate(row));
            assertNotNull(row.getItemId());
            return row.getItemId();
        }
    }

    private void readItem(String section, Long itemId) {
        if ("EDUCATION".equals(section)) assertNotNull(mapper.findEducationItem(STUDENT_ID, itemId));
        else if ("CAREER".equals(section)) assertNotNull(mapper.findCareerItem(STUDENT_ID, itemId));
        else assertNotNull(mapper.findCertificate(STUDENT_ID, itemId));
    }

    private Map<String, Object> recordRow(String section, Long studentId, Long itemId) {
        List<Map<String, Object>> rows = recordRows(section, studentId, itemId);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    private List<Map<String, Object>> recordRows(String section, Long studentId, Long itemId) {
        return jdbc.queryForList("SELECT * FROM " + table(section) + " WHERE student_id = ? AND id = ?",
                studentId, itemId);
    }

    private ProfileData.Project projectFixture() {
        assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
        var project = new ProfileData.Project();
        project.setTitle(marker());
        project.setPeriodText("모의 6주");
        project.setTeamSize(4);
        project.setTechStack(json.createArrayNode().add("Java"));
        project.setRoleNote("모의 API 설계와 검증 역할 원문");
        project.setReadme("# 모의 README\n재추출 근거 원문을 유지합니다.");
        assertEquals(1, mapper.insertProject(STUDENT_ID, project));
        assertNotNull(project.getItemId());
        jdbc.update("""
                UPDATE student_project SET portfolio_file_url = ?, source = 'REGISTERED'
                WHERE student_id = ? AND id = ?
                """, "모의_보존할_포트폴리오.pdf", STUDENT_ID, project.getItemId());
        ProfileData.Project stored = mapper.findProjectItem(STUDENT_ID, project.getItemId());
        assertNotNull(stored);
        assertTrue(stored.isIncludeInApply());
        return stored;
    }

    private List<Map<String, Object>> projectRows(Long studentId) {
        return jdbc.queryForList("SELECT * FROM student_project WHERE student_id = ? ORDER BY id", studentId);
    }

    private JsonNode findInput(JsonNode source, String section, Long itemId) {
        String field = switch (section) {
            case "EDUCATION" -> "education";
            case "CAREER" -> "careers";
            case "CERTIFICATES" -> "certificates";
            default -> "projects";
        };
        for (JsonNode item : source.path(field)) {
            if (item.path("itemId").asLong() == itemId) return item;
        }
        return null;
    }

    private String table(String section) {
        return switch (section) {
            case "EDUCATION" -> "student_education";
            case "CAREER" -> "student_career";
            default -> "student_certificate";
        };
    }

    private String nameField(String section) {
        return switch (section) {
            case "EDUCATION" -> "schoolName";
            case "CAREER" -> "companyName";
            default -> "name";
        };
    }

    private String nameColumn(String section) {
        return switch (section) {
            case "EDUCATION" -> "school_name";
            case "CAREER" -> "company_name";
            default -> "name";
        };
    }

    private String optionalField(String section) {
        return switch (section) {
            case "EDUCATION" -> "major";
            case "CAREER" -> "position";
            default -> "issuer";
        };
    }

    private String optionalColumn(String section) {
        return optionalField(section);
    }

    private static String marker() {
        return "모의 서류 저장 검증-" + UUID.randomUUID();
    }
}
