// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ieum.ai.AiClient;
import com.ieum.ai.MockAiClient;
import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IEUM_PROFILE_DB_TEST=true일 때만 기존 로컬 MySQL과 시드 수료생 1을 사용한다.
 * 모든 쓰기는 테스트의 외부 트랜잭션에서 롤백한다. InnoDB AUTO_INCREMENT 공백은 남을 수 있다.
 * .env 값과 등록 자료 원문은 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_PROFILE_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.config.import=optional:file:.env[.properties],optional:file:backend/.env[.properties]",
        "spring.sql.init.mode=never",
        "app.ai.mock=true"
})
class ProfileItemDatabaseTest {

    private static final Long STUDENT_ID = 1L;

    @Autowired
    private ProfileItemService service;

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
    void requireTransactionalTablesAndExistingSeed() {
        List<String> engines = jdbc.queryForList("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('student_certificate', 'competency_profile', 'student_cover_letter',
                                     'student_education', 'student_career', 'student_project', 'student')
                """, String.class);
        assertEquals(7, engines.size(), "기존 스키마가 준비되어 있어야 한다");
        assertTrue(engines.stream().allMatch("InnoDB"::equalsIgnoreCase),
                "실제 데이터를 보호하기 위해 InnoDB 테이블에서만 실행한다");
        assertNotNull(mapper.findStudent(STUDENT_ID), "시드 수료생 1이 있어야 한다");
        transactions = new TransactionTemplate(transactionManager);
    }

    @Test
    void generatedCertificateKeyAndCompetencyInsertAndUpdateUseRealMySqlAndRollback() {
        Baseline baseline = baseline();
        String directName = marker();
        String firstName = marker();
        String secondName = marker();
        JsonNode firstProfile = competency(firstName);
        JsonNode secondProfile = competency(secondName);
        when(aiClient.extractProfile(any(JsonNode.class))).thenReturn(
                json.createObjectNode().set("profile", firstProfile),
                json.createObjectNode().set("profile", secondProfile));

        try {
            transactions.executeWithoutResult(status -> {
                // 어떤 검증에서 실패해도 기존 행을 커밋하지 않는다.
                status.setRollbackOnly();
                assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                jdbc.update("DELETE FROM competency_profile WHERE student_id = ?", STUDENT_ID);
                assertEquals(0L, competencyCount());

                var certificate = new ProfileData.Certificate();
                certificate.setStudentId(STUDENT_ID);
                certificate.setName(directName);
                assertEquals(1, mapper.insertCertificate(certificate));
                assertNotNull(certificate.getItemId(), "MyBatis가 생성된 ID를 돌려줘야 한다");
                assertTrue(certificate.getItemId() > 0);
                StudentProfileResponse.Certificate stored = mapper.findCertificates(STUDENT_ID).stream()
                        .filter(item -> item.itemId().equals(certificate.getItemId()))
                        .findFirst().orElseThrow();
                assertEquals("USER", stored.source());
                assertEquals(directName, stored.name());
                assertNull(stored.issuer());
                assertNull(stored.acquiredOn());

                StudentProfileResponse first = service.addItem(STUDENT_ID, request(firstName, null, null));
                assertEquals(1L, competencyCount());
                assertStoredCompetency(first, firstProfile);
                assertM8Shape(first);
                StudentProfileResponse.Certificate added = findCertificate(first, firstName);
                assertTrue(added.itemId() > certificate.getItemId());
                assertEquals("USER", added.source());
                assertNull(added.issuer());
                assertNull(added.acquiredOn());
                String firstHash = sourceHash();
                assertTrue(firstHash.matches("[0-9a-f]{64}"));

                StudentProfileResponse second = service.addItem(
                        STUDENT_ID, request(secondName, "모의 발급기관", LocalDate.of(2026, 9, 1)));
                assertEquals(1L, competencyCount(), "두 번째 저장은 같은 프로필 행을 갱신해야 한다");
                assertStoredCompetency(second, secondProfile);
                assertM8Shape(second);
                assertNotNull(findCertificate(second, firstName));
                StudentProfileResponse.Certificate secondAdded = findCertificate(second, secondName);
                assertEquals("USER", secondAdded.source());
                assertEquals("모의 발급기관", secondAdded.issuer());
                assertEquals(LocalDate.of(2026, 9, 1), secondAdded.acquiredOn());
                assertFalse(firstHash.equals(sourceHash()), "등록 원문이 바뀌면 해시도 바뀌어야 한다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
    }

    @Test
    void aiFailureMarksServiceTransactionRollbackOnlyAndLeavesExistingRowsUnchanged() {
        Baseline baseline = baseline();
        String name = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    BusinessException failure = assertThrows(BusinessException.class,
                            () -> service.addItem(STUDENT_ID, request(name, null, null)));
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    // 테스트가 rollbackOnly를 설정하기 전에 실제 서비스 프록시의 전파를 검증한다.
                    assertTrue(status.isRollbackOnly(),
                            "서비스 @Transactional이 AI 실패를 롤백 대상으로 표시해야 한다");
                    assertEquals(1L, jdbc.queryForObject("""
                            SELECT COUNT(*) FROM student_certificate WHERE student_id = ? AND name = ?
                            """, Long.class, STUDENT_ID, name),
                            "실제 INSERT 이후 AI 실패가 발생해야 한다");
                } finally {
                    // 서비스의 트랜잭션 처리가 회귀해도 테스트 데이터는 커밋하지 않는다.
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void partialUpdateNullClearingAndRegisteredDeletionUseRealMySqlAndRollback() {
        Baseline baseline = baseline();
        String originalName = marker();
        String changedName = marker();
        LocalDate acquiredOn = LocalDate.of(2026, 9, 1);
        MockAiClient mockFixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = mockFixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> mockFixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                var certificate = new ProfileData.Certificate();
                certificate.setStudentId(STUDENT_ID);
                certificate.setName(originalName);
                certificate.setIssuer("모의 발급 기관");
                certificate.setAcquiredOn(acquiredOn);
                assertEquals(1, mapper.insertCertificate(certificate));
                assertNotNull(certificate.getItemId());
                Long itemId = certificate.getItemId();
                jdbc.update("UPDATE student_certificate SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findCertificate(STUDENT_ID, itemId).source());

                StudentProfileResponse renamed = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("CERTIFICATES", json.createObjectNode().put("name", changedName)));
                StudentProfileResponse.Certificate changed = findCertificateById(renamed, itemId);
                assertEquals(changedName, changed.name());
                assertEquals("모의 발급 기관", changed.issuer(), "누락된 발급 기관은 유지해야 한다");
                assertEquals(acquiredOn, changed.acquiredOn(), "누락된 취득일은 유지해야 한다");
                assertEquals("USER", changed.source());
                assertStoredCompetency(renamed, expectedCompetency);
                assertM8Shape(renamed);
                String renamedHash = sourceHash();
                assertTrue(renamedHash.matches("[0-9a-f]{64}"));

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("CERTIFICATES", json.createObjectNode()
                                .putNull("issuer").putNull("acquiredOn")));
                StudentProfileResponse.Certificate emptied = findCertificateById(cleared, itemId);
                assertEquals(changedName, emptied.name());
                assertNull(emptied.issuer());
                assertNull(emptied.acquiredOn());
                assertStoredCompetency(cleared, expectedCompetency);
                String clearedHash = sourceHash();
                assertFalse(renamedHash.equals(clearedHash), "null로 비운 원문도 새 해시에 반영해야 한다");

                jdbc.update("UPDATE student_certificate SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findCertificate(STUDENT_ID, itemId).source());
                StudentProfileResponse deleted = service.deleteItem(STUDENT_ID, itemId, "CERTIFICATES");
                assertTrue(deleted.certificates().stream().noneMatch(item -> item.itemId().equals(itemId)));
                assertNull(mapper.findCertificate(STUDENT_ID, itemId));
                assertStoredCompetency(deleted, expectedCompetency);
                assertM8Shape(deleted);
                assertFalse(clearedHash.equals(sourceHash()), "삭제한 원문도 새 해시에 반영해야 한다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verify(aiClient, times(3)).extractProfile(any(JsonNode.class));
    }

    @Test
    void anotherStudentsCertificateCannotBeUpdatedOrDeletedAndNeitherProfileChanges() {
        Baseline baseline = baseline();
        Long otherStudentId = 2L;
        StudentProfileResponse otherBaseline = profiles.getProfile(otherStudentId);
        assertFalse(otherBaseline.certificates().isEmpty(), "시드 수료생 2의 자격증이 있어야 한다");
        Long foreignItemId = otherBaseline.certificates().get(0).itemId();

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertThrows(ProfileItemNotFoundException.class,
                            () -> service.updateItem(STUDENT_ID, foreignItemId,
                                    new ProfileItemPatchRequest("CERTIFICATES",
                                            json.createObjectNode().put("name", marker()))));
                    assertThrows(ProfileItemNotFoundException.class,
                            () -> service.deleteItem(STUDENT_ID, foreignItemId, "CERTIFICATES"));
                    assertNull(mapper.findCertificate(STUDENT_ID, foreignItemId));
                    assertTrue(otherBaseline.equals(profiles.getProfile(otherStudentId)),
                            "다른 수료생의 자격증과 역량 프로필을 변경하면 안 된다");
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(otherStudentId)),
                    "롤백 후 다른 수료생의 프로필도 그대로여야 한다");
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "DELETE"})
    void aiFailureRollsBackCertificateUpdateOrDeleteAndPreservesOriginalCompetency(String operation) {
        Baseline baseline = baseline();
        assertFalse(baseline.profile().certificates().isEmpty(), "시드 수료생 1의 자격증이 있어야 한다");
        Long itemId = baseline.profile().certificates().get(0).itemId();
        String changedName = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("UPDATE".equals(operation)) {
                            service.updateItem(STUDENT_ID, itemId, new ProfileItemPatchRequest(
                                    "CERTIFICATES", json.createObjectNode().put("name", changedName)));
                        } else {
                            service.deleteItem(STUDENT_ID, itemId, "CERTIFICATES");
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "서비스가 AI 실패를 롤백 대상으로 표시해야 한다");
                    if ("UPDATE".equals(operation)) {
                        StudentProfileResponse.Certificate changed = mapper.findCertificate(STUDENT_ID, itemId);
                        assertNotNull(changed);
                        assertEquals(changedName, changed.name(), "실제 UPDATE 뒤 AI가 실패해야 한다");
                        assertEquals("USER", changed.source());
                    } else {
                        assertNull(mapper.findCertificate(STUDENT_ID, itemId),
                                "실제 DELETE 뒤 AI가 실패해야 한다");
                    }
                } finally {
                    // 서비스의 트랜잭션 처리가 회귀해도 원본 행은 커밋하지 않는다.
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void educationGeneratedKeyAddPartialUpdateNullClearingAndDeletionUseRealMySqlAndRollback() {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String directSchoolName = marker();
        String addedSchoolName = marker();
        String changedSchoolName = marker();
        LocalDate graduatedOn = LocalDate.of(2026, 2, 1);
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                var directEducation = new ProfileData.Education();
                directEducation.setStudentId(STUDENT_ID);
                directEducation.setSchoolName(directSchoolName);
                assertEquals(1, mapper.insertEducation(directEducation));
                assertNotNull(directEducation.getItemId(), "MyBatis가 생성된 학력 ID를 돌려줘야 한다");
                assertTrue(directEducation.getItemId() > 0);
                StudentProfileResponse.Education direct = mapper.findEducationItem(
                        STUDENT_ID, directEducation.getItemId());
                assertNotNull(direct);
                assertEquals(directSchoolName, direct.schoolName());
                assertEquals("USER", direct.source());
                assertNull(direct.major());
                assertNull(direct.degree());
                assertNull(direct.status());
                assertNull(direct.graduatedOn());

                StudentProfileResponse added = service.addItem(STUDENT_ID,
                        new ProfileItemRequest("EDUCATION", json.createObjectNode()
                                .put("schoolName", addedSchoolName)
                                .put("major", "모의 전공")
                                .put("degree", "BACHELOR")
                                .put("status", "GRADUATED")
                                .put("graduatedOn", graduatedOn.toString())));
                StudentProfileResponse.Education created = findEducation(added, addedSchoolName);
                Long itemId = created.itemId();
                assertTrue(itemId > directEducation.getItemId());
                assertEquals("USER", created.source());
                assertEquals("모의 전공", created.major());
                assertEquals("BACHELOR", created.degree());
                assertEquals("GRADUATED", created.status());
                assertEquals(graduatedOn, created.graduatedOn());
                assertStoredCompetency(added, expectedCompetency);
                assertM8Shape(added);
                String addedHash = sourceHash();
                assertTrue(addedHash.matches("[0-9a-f]{64}"));

                jdbc.update("UPDATE student_education SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findEducationItem(STUDENT_ID, itemId).source());
                StudentProfileResponse renamed = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("EDUCATION",
                                json.createObjectNode().put("schoolName", changedSchoolName)));
                StudentProfileResponse.Education changed = findEducationById(renamed, itemId);
                assertEquals(changedSchoolName, changed.schoolName());
                assertEquals("USER", changed.source());
                assertEquals("모의 전공", changed.major(), "누락된 전공은 유지해야 한다");
                assertEquals("BACHELOR", changed.degree(), "누락된 학위는 유지해야 한다");
                assertEquals("GRADUATED", changed.status(), "누락된 졸업 상태는 유지해야 한다");
                assertEquals(graduatedOn, changed.graduatedOn(), "누락된 졸업일은 유지해야 한다");
                assertStoredCompetency(renamed, expectedCompetency);
                assertM8Shape(renamed);
                String renamedHash = sourceHash();
                assertFalse(addedHash.equals(renamedHash), "수정한 학교명을 원문 해시에 반영해야 한다");

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("EDUCATION", json.createObjectNode()
                                .putNull("major").putNull("degree").putNull("status").putNull("graduatedOn")));
                StudentProfileResponse.Education emptied = findEducationById(cleared, itemId);
                assertEquals(changedSchoolName, emptied.schoolName());
                assertEquals("USER", emptied.source());
                assertNull(emptied.major());
                assertNull(emptied.degree());
                assertNull(emptied.status());
                assertNull(emptied.graduatedOn());
                assertStoredCompetency(cleared, expectedCompetency);
                assertM8Shape(cleared);
                String clearedHash = sourceHash();
                assertFalse(renamedHash.equals(clearedHash), "null로 비운 학력도 새 해시에 반영해야 한다");

                jdbc.update("UPDATE student_education SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findEducationItem(STUDENT_ID, itemId).source());
                StudentProfileResponse deleted = service.deleteItem(STUDENT_ID, itemId, "EDUCATION");
                assertTrue(deleted.education().stream().noneMatch(item -> itemId.equals(item.itemId())));
                assertNull(mapper.findEducationItem(STUDENT_ID, itemId));
                assertNotNull(findEducationById(deleted, directEducation.getItemId()),
                        "삭제 대상 이외의 학력을 유지해야 한다");
                assertStoredCompetency(deleted, expectedCompetency);
                assertM8Shape(deleted);
                assertFalse(clearedHash.equals(sourceHash()), "삭제한 학력도 새 해시에 반영해야 한다");
                assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 변경하면 안 된다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        verify(aiClient, times(4)).extractProfile(any(JsonNode.class));
    }

    @Test
    void anotherStudentsEducationCannotBeUpdatedOrDeletedAndNeitherProfileChanges() {
        Baseline baseline = baseline();
        Long otherStudentId = 2L;
        StudentProfileResponse otherBaseline = profiles.getProfile(otherStudentId);
        assertFalse(otherBaseline.education().isEmpty(), "시드 수료생 2의 학력이 있어야 한다");
        Long foreignItemId = otherBaseline.education().get(0).itemId();

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertThrows(ProfileItemNotFoundException.class,
                            () -> service.updateItem(STUDENT_ID, foreignItemId,
                                    new ProfileItemPatchRequest("EDUCATION",
                                            json.createObjectNode().put("schoolName", marker()))));
                    assertThrows(ProfileItemNotFoundException.class,
                            () -> service.deleteItem(STUDENT_ID, foreignItemId, "EDUCATION"));
                    assertNull(mapper.findEducationItem(STUDENT_ID, foreignItemId));
                    assertTrue(otherBaseline.equals(profiles.getProfile(otherStudentId)),
                            "다른 수료생의 학력과 역량 프로필을 변경하면 안 된다");
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(otherStudentId)),
                    "롤백 후 다른 수료생의 프로필도 그대로여야 한다");
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "UPDATE", "DELETE"})
    void aiFailureRollsBackEducationWriteAndPreservesBothProfiles(String operation) {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String originalSchoolName = marker();
        String changedSchoolName = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    var education = new ProfileData.Education();
                    if (!"ADD".equals(operation)) {
                        assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                        education.setStudentId(STUDENT_ID);
                        education.setSchoolName(originalSchoolName);
                        assertEquals(1, mapper.insertEducation(education));
                        assertNotNull(education.getItemId());
                    }

                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("ADD".equals(operation)) {
                            service.addItem(STUDENT_ID, new ProfileItemRequest("EDUCATION",
                                    json.createObjectNode().put("schoolName", changedSchoolName)));
                        } else if ("UPDATE".equals(operation)) {
                            service.updateItem(STUDENT_ID, education.getItemId(),
                                    new ProfileItemPatchRequest("EDUCATION",
                                            json.createObjectNode().put("schoolName", changedSchoolName)));
                        } else {
                            service.deleteItem(STUDENT_ID, education.getItemId(), "EDUCATION");
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "서비스가 AI 실패를 롤백 대상으로 표시해야 한다");
                    if ("ADD".equals(operation)) {
                        assertEquals(1L, jdbc.queryForObject("""
                                SELECT COUNT(*) FROM student_education WHERE student_id = ? AND school_name = ?
                                """, Long.class, STUDENT_ID, changedSchoolName),
                                "실제 INSERT 뒤 AI가 실패해야 한다");
                    } else if ("UPDATE".equals(operation)) {
                        StudentProfileResponse.Education changed = mapper.findEducationItem(
                                STUDENT_ID, education.getItemId());
                        assertNotNull(changed);
                        assertEquals(changedSchoolName, changed.schoolName(), "실제 UPDATE 뒤 AI가 실패해야 한다");
                        assertEquals("USER", changed.source());
                    } else {
                        assertNull(mapper.findEducationItem(STUDENT_ID, education.getItemId()),
                                "실제 DELETE 뒤 AI가 실패해야 한다");
                    }
                    assertTrue(baseline.competencies().equals(competencyRows()),
                            "실패한 AI 결과로 기존 역량 프로필을 변경하면 안 된다");
                    assertTrue(otherBaseline.equals(profiles.getProfile(2L)),
                            "다른 수료생 자료를 변경하면 안 된다");
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void careerGeneratedKeyAddPartialUpdateCurrentEmploymentAndRegisteredDeletionUseRealMySqlAndRollback() {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String directCompanyName = marker();
        String addedCompanyName = marker();
        String changedCompanyName = marker();
        LocalDate startedOn = LocalDate.of(2025, 3, 1);
        LocalDate endedOn = LocalDate.of(2026, 2, 28);
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                var directCareer = new ProfileData.Career();
                directCareer.setStudentId(STUDENT_ID);
                directCareer.setCompanyName(directCompanyName);
                assertEquals(1, mapper.insertCareer(directCareer));
                assertNotNull(directCareer.getItemId(), "MyBatis가 생성된 경력 ID를 돌려줘야 한다");
                assertTrue(directCareer.getItemId() > 0);
                StudentProfileResponse.Career direct = mapper.findCareerItem(STUDENT_ID, directCareer.getItemId());
                assertNotNull(direct);
                assertEquals(directCompanyName, direct.companyName());
                assertEquals("USER", direct.source());
                assertNull(direct.position());
                assertNull(direct.startedOn());
                assertNull(direct.endedOn());
                assertNull(direct.description());

                StudentProfileResponse added = service.addItem(STUDENT_ID,
                        new ProfileItemRequest("CAREER", json.createObjectNode()
                                .put("companyName", addedCompanyName)
                                .put("position", "모의 개발 담당")
                                .put("startedOn", startedOn.toString())
                                .put("endedOn", endedOn.toString())
                                .put("description", "모의 API 구현 경험")));
                StudentProfileResponse.Career created = findCareer(added, addedCompanyName);
                Long itemId = created.itemId();
                assertTrue(itemId > directCareer.getItemId());
                assertEquals("USER", created.source());
                assertEquals("모의 개발 담당", created.position());
                assertEquals(startedOn, created.startedOn());
                assertEquals(endedOn, created.endedOn());
                assertEquals("모의 API 구현 경험", created.description());
                assertStoredCompetency(added, expectedCompetency);
                assertM8Shape(added);
                String addedHash = sourceHash();
                assertTrue(addedHash.matches("[0-9a-f]{64}"));

                jdbc.update("UPDATE student_career SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findCareerItem(STUDENT_ID, itemId).source());
                StudentProfileResponse renamed = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("CAREER",
                                json.createObjectNode().put("companyName", changedCompanyName)));
                StudentProfileResponse.Career changed = findCareerById(renamed, itemId);
                assertEquals(changedCompanyName, changed.companyName());
                assertEquals("USER", changed.source());
                assertEquals("모의 개발 담당", changed.position(), "누락된 직무는 유지해야 한다");
                assertEquals(startedOn, changed.startedOn(), "누락된 입사일은 유지해야 한다");
                assertEquals(endedOn, changed.endedOn(), "누락된 퇴사일은 유지해야 한다");
                assertEquals("모의 API 구현 경험", changed.description(), "누락된 경력 설명은 유지해야 한다");
                assertStoredCompetency(renamed, expectedCompetency);
                assertM8Shape(renamed);
                String renamedHash = sourceHash();
                assertFalse(addedHash.equals(renamedHash), "수정한 회사명을 원문 해시에 반영해야 한다");

                StudentProfileResponse current = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("CAREER", json.createObjectNode().putNull("endedOn")));
                StudentProfileResponse.Career employed = findCareerById(current, itemId);
                assertEquals(startedOn, employed.startedOn());
                assertNull(employed.endedOn(), "퇴사일 null은 재직 중인 경력으로 저장해야 한다");
                assertEquals("모의 개발 담당", employed.position());
                assertEquals("모의 API 구현 경험", employed.description());
                assertStoredCompetency(current, expectedCompetency);
                assertM8Shape(current);
                String currentHash = sourceHash();
                assertFalse(renamedHash.equals(currentHash), "재직 중으로 바꾼 경력도 해시에 반영해야 한다");

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("CAREER", json.createObjectNode()
                                .putNull("position").putNull("startedOn").putNull("description")));
                StudentProfileResponse.Career emptied = findCareerById(cleared, itemId);
                assertEquals(changedCompanyName, emptied.companyName());
                assertEquals("USER", emptied.source());
                assertNull(emptied.position());
                assertNull(emptied.startedOn());
                assertNull(emptied.endedOn());
                assertNull(emptied.description());
                assertStoredCompetency(cleared, expectedCompetency);
                assertM8Shape(cleared);
                String clearedHash = sourceHash();
                assertFalse(currentHash.equals(clearedHash), "null로 비운 경력도 새 해시에 반영해야 한다");

                jdbc.update("UPDATE student_career SET source = 'REGISTERED' WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);
                assertEquals("REGISTERED", mapper.findCareerItem(STUDENT_ID, itemId).source());
                StudentProfileResponse deleted = service.deleteItem(STUDENT_ID, itemId, "CAREER");
                assertTrue(deleted.careers().stream().noneMatch(item -> itemId.equals(item.itemId())));
                assertNull(mapper.findCareerItem(STUDENT_ID, itemId));
                assertNotNull(findCareerById(deleted, directCareer.getItemId()), "삭제 대상 이외의 경력을 유지해야 한다");
                assertStoredCompetency(deleted, expectedCompetency);
                assertM8Shape(deleted);
                assertFalse(clearedHash.equals(sourceHash()), "삭제한 경력도 새 해시에 반영해야 한다");
                assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 변경하면 안 된다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        verify(aiClient, times(5)).extractProfile(any(JsonNode.class));
    }

    @Test
    void anotherStudentsCareerCannotBeUpdatedOrDeletedAndNeitherProfileChanges() {
        Baseline baseline = baseline();
        Long otherStudentId = 2L;
        StudentProfileResponse otherBaseline = profiles.getProfile(otherStudentId);
        String companyName = marker();

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertEquals(otherStudentId, mapper.lockStudent(otherStudentId));
                var foreignCareer = new ProfileData.Career();
                foreignCareer.setStudentId(otherStudentId);
                foreignCareer.setCompanyName(companyName);
                assertEquals(1, mapper.insertCareer(foreignCareer));
                assertNotNull(foreignCareer.getItemId());
                Long foreignItemId = foreignCareer.getItemId();
                StudentProfileResponse otherWithFixture = profiles.getProfile(otherStudentId);

                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, foreignItemId,
                                new ProfileItemPatchRequest("CAREER",
                                        json.createObjectNode().put("companyName", marker()))));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.deleteItem(STUDENT_ID, foreignItemId, "CAREER"));
                assertNull(mapper.findCareerItem(STUDENT_ID, foreignItemId));
                assertEquals(companyName, mapper.findCareerItem(otherStudentId, foreignItemId).companyName());
                assertTrue(baseline.profile().equals(profiles.getProfile(STUDENT_ID)),
                        "다른 수료생 항목을 요청해도 본인 프로필은 변경하면 안 된다");
                assertTrue(otherWithFixture.equals(profiles.getProfile(otherStudentId)),
                        "다른 수료생의 경력과 역량 프로필을 변경하면 안 된다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(otherStudentId)),
                    "롤백 후 다른 수료생의 프로필도 그대로여야 한다");
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADD", "UPDATE", "DELETE"})
    void aiFailureRollsBackCareerWriteAndPreservesBothProfiles(String operation) {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String originalCompanyName = marker();
        String changedCompanyName = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    var career = new ProfileData.Career();
                    if (!"ADD".equals(operation)) {
                        assertEquals(STUDENT_ID, mapper.lockStudent(STUDENT_ID));
                        career.setStudentId(STUDENT_ID);
                        career.setCompanyName(originalCompanyName);
                        assertEquals(1, mapper.insertCareer(career));
                        assertNotNull(career.getItemId());
                    }

                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("ADD".equals(operation)) {
                            service.addItem(STUDENT_ID, new ProfileItemRequest("CAREER",
                                    json.createObjectNode().put("companyName", changedCompanyName)));
                        } else if ("UPDATE".equals(operation)) {
                            service.updateItem(STUDENT_ID, career.getItemId(),
                                    new ProfileItemPatchRequest("CAREER",
                                            json.createObjectNode().put("companyName", changedCompanyName)));
                        } else {
                            service.deleteItem(STUDENT_ID, career.getItemId(), "CAREER");
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "서비스가 AI 실패를 롤백 대상으로 표시해야 한다");
                    if ("ADD".equals(operation)) {
                        assertEquals(1L, jdbc.queryForObject("""
                                SELECT COUNT(*) FROM student_career WHERE student_id = ? AND company_name = ?
                                """, Long.class, STUDENT_ID, changedCompanyName),
                                "실제 INSERT 뒤 AI가 실패해야 한다");
                    } else if ("UPDATE".equals(operation)) {
                        StudentProfileResponse.Career changed = mapper.findCareerItem(STUDENT_ID, career.getItemId());
                        assertNotNull(changed);
                        assertEquals(changedCompanyName, changed.companyName(), "실제 UPDATE 뒤 AI가 실패해야 한다");
                        assertEquals("USER", changed.source());
                    } else {
                        assertNull(mapper.findCareerItem(STUDENT_ID, career.getItemId()),
                                "실제 DELETE 뒤 AI가 실패해야 한다");
                    }
                    assertTrue(baseline.competencies().equals(competencyRows()),
                            "실패한 AI 결과로 기존 역량 프로필을 변경하면 안 된다");
                    assertTrue(otherBaseline.equals(profiles.getProfile(2L)),
                            "다른 수료생 자료를 변경하면 안 된다");
                } finally {
                    // 서비스의 트랜잭션 처리가 회귀해도 모의 경력은 커밋하지 않는다.
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void coverLetterInsertAndUpdatePreserveOriginalTextAndRefreshCompetencyInRealMySql() {
        Baseline baseline = baseline();
        List<CoverLetterRow> originalCoverLetter = coverLetterRows();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String firstContent = marker() + "\n모의 프로젝트에서 API를 구현했습니다.";
        String secondContent = marker() + "\n\"문제 해결\"과 \\기술 경험을 보완했습니다.";
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                mapper.lockStudent(STUDENT_ID);
                jdbc.update("DELETE FROM student_cover_letter WHERE student_id = ?", STUDENT_ID);

                StudentProfileResponse first = service.updateCoverLetter(STUDENT_ID, firstContent);
                assertNotNull(first.coverLetter());
                assertEquals(firstContent, first.coverLetter().content());
                assertNotNull(first.coverLetter().updatedAt());
                assertEquals(ZoneOffset.ofHours(9), first.coverLetter().updatedAt().getOffset());
                assertStoredCompetency(first, expectedCompetency);
                assertM8Shape(first);
                String firstHash = sourceHash();

                StudentProfileResponse second = service.updateCoverLetter(STUDENT_ID, secondContent);
                assertEquals(secondContent, second.coverLetter().content());
                assertEquals(secondContent, mapper.findStudent(STUDENT_ID).getCoverLetterContent());
                assertEquals(1L, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM student_cover_letter WHERE student_id = ?", Long.class, STUDENT_ID));
                assertStoredCompetency(second, expectedCompetency);
                assertM8Shape(second);
                assertFalse(firstHash.equals(sourceHash()), "새 자기소개서 원문을 해시에 반영해야 한다");
                assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 변경하면 안 된다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(originalCoverLetter.equals(coverLetterRows()), "기존 자기소개서 원문·시각을 보존해야 한다");
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        verify(aiClient, times(2)).extractProfile(any(JsonNode.class));
    }

    @Test
    void aiFailureRollsBackCoverLetterWriteAndPreservesOriginalProfile() {
        Baseline baseline = baseline();
        List<CoverLetterRow> originalCoverLetter = coverLetterRows();
        String changedContent = marker() + "\n모의 수정 자기소개서";
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));

        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    BusinessException failure = assertThrows(BusinessException.class,
                            () -> service.updateCoverLetter(STUDENT_ID, changedContent));
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "서비스가 AI 실패를 롤백 대상으로 표시해야 한다");
                    assertEquals(changedContent, jdbc.queryForObject(
                            "SELECT content FROM student_cover_letter WHERE student_id = ?", String.class, STUDENT_ID),
                            "실제 원문 저장 이후 AI가 실패해야 한다");
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(originalCoverLetter.equals(coverLetterRows()), "실패 시 기존 원문·시각을 복구해야 한다");
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void projectCrudKeepsPartialValuesAndLetsOnlyM8EditOriginalsBeforeReextracting() {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String title = marker();
        String renamedTitle = marker();
        String originalReadme = "# 모의 README\nAPI 구현 근거 원문";
        String originalRole = "모의 프로젝트의 API 설계와 검증을 담당했습니다.";
        String changedReadme = "# 모의 수정 README\n트랜잭션 처리 근거 원문";
        String changedRole = "모의 트랜잭션 설계와 오류 처리 구현을 담당했습니다.";
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var item = json.createObjectNode().put("title", title).put("periodText", "모의 6주")
                        .put("teamSize", 4).put("roleNote", originalRole).put("readme", originalReadme);
                item.putArray("techStack").add("Java").add("Spring Boot");
                StudentProfileResponse added = service.addItem(STUDENT_ID, new ProfileItemRequest("PROJECTS", item));
                StudentProfileResponse.Project created = added.projects().stream()
                        .filter(project -> title.equals(project.title())).findFirst().orElseThrow();
                Long itemId = created.itemId();
                assertNotNull(itemId, "MyBatis가 생성한 프로젝트 ID를 반환해야 한다");
                assertTrue(itemId > 0);
                assertTrue(created.includeInApply());
                ProfileData.Project rawAdded = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals(originalRole, rawAdded.getRoleNote());
                assertEquals(originalReadme, rawAdded.getReadme());
                assertEquals("USER", rawAdded.getSource());
                assertStoredCompetency(added, expectedCompetency);
                assertM8Shape(added);
                String addedHash = sourceHash();
                jdbc.update("""
                        UPDATE student_project SET source = 'REGISTERED', portfolio_file_url = ?
                        WHERE student_id = ? AND id = ?
                        """, "모의_프로젝트.pdf", STUDENT_ID, itemId);

                StudentProfileResponse renamed = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PROJECTS", json.createObjectNode().put("title", renamedTitle)));
                ProfileData.Project rawRenamed = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals(renamedTitle, rawRenamed.getTitle());
                assertEquals("모의 6주", rawRenamed.getPeriodText());
                assertEquals(4, rawRenamed.getTeamSize());
                assertEquals(List.of("Java", "Spring Boot"), renamed.projects().stream()
                        .filter(project -> itemId.equals(project.itemId())).findFirst().orElseThrow().techStack());
                assertEquals(originalRole, rawRenamed.getRoleNote());
                assertEquals(originalReadme, rawRenamed.getReadme());
                assertEquals("모의_프로젝트.pdf", rawRenamed.getPortfolioFileName());
                assertEquals("USER", rawRenamed.getSource());
                assertFalse(addedHash.equals(sourceHash()));
                String renamedHash = sourceHash();

                StudentProfileResponse originalsChanged = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PROJECTS", json.createObjectNode()
                                .put("roleNote", changedRole).put("readme", changedReadme)));
                ProfileData.Project rawChanged = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals(changedRole, rawChanged.getRoleNote());
                assertEquals(changedReadme, rawChanged.getReadme());
                assertEquals(renamedTitle, rawChanged.getTitle());
                assertEquals("모의_프로젝트.pdf", rawChanged.getPortfolioFileName());
                assertStoredCompetency(originalsChanged, expectedCompetency);
                assertM8Shape(originalsChanged);
                assertFalse(renamedHash.equals(sourceHash()), "M8 원문 수정도 AI 입력 해시에 반영해야 한다");

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PROJECTS", json.createObjectNode()
                                .putNull("periodText").putNull("teamSize").putNull("techStack")
                                .putNull("roleNote").putNull("readme")));
                ProfileData.Project rawCleared = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals(renamedTitle, rawCleared.getTitle());
                assertNull(rawCleared.getPeriodText());
                assertNull(rawCleared.getTeamSize());
                assertTrue(rawCleared.getTechStack() == null || rawCleared.getTechStack().isNull());
                assertNull(rawCleared.getRoleNote());
                assertNull(rawCleared.getReadme());
                assertEquals("모의_프로젝트.pdf", rawCleared.getPortfolioFileName());
                assertStoredCompetency(cleared, expectedCompetency);
                assertM8Shape(cleared);

                StudentProfileResponse deleted = service.deleteItem(STUDENT_ID, itemId, "PROJECTS");
                assertNull(mapper.findProjectItem(STUDENT_ID, itemId));
                assertTrue(deleted.projects().stream().noneMatch(project -> itemId.equals(project.itemId())));
                assertStoredCompetency(deleted, expectedCompetency);
                assertM8Shape(deleted);
                assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)), "다른 수료생 자료를 보존해야 한다");
        }
        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient, times(5)).extractProfile(requests.capture());
        JsonNode changedInput = requests.getAllValues().get(2).get("projects");
        JsonNode changedProject = null;
        for (JsonNode project : changedInput) {
            if (renamedTitle.equals(project.path("title").asText())) changedProject = project;
        }
        assertNotNull(changedProject);
        assertEquals(changedReadme, changedProject.path("readme").asText(),
                "AI는 M8 응답에 생략한 README 원문까지 읽어야 한다");
        assertEquals(changedRole, changedProject.path("roleNote").asText());
    }

    @Test
    void documentProjectAddReturnsGeneratedIdAndUpdatePreservesReadmeRoleAndPortfolio() {
        Baseline baseline = baseline();
        MockAiClient fixture = new MockAiClient(json, null);
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var add = json.createObjectNode().put("title", marker()).put("periodText", "모의 6주")
                        .put("teamSize", 4).put("role", "백엔드")
                        .put("roleNote", "서류가 보내도 저장하지 않는 역할 기록")
                        .put("readme", "서류가 보내도 저장하지 않는 README");
                add.putArray("techStack").add("Java");
                add.putArray("bullets").addObject().put("text", "모의 공고용 AI 문장");
                Long addedId = profiles.addItem(STUDENT_ID, "PROJECTS", add);
                assertNotNull(addedId);
                assertTrue(addedId > 0);
                ProfileData.Project added = mapper.findProjectItem(STUDENT_ID, addedId);
                assertNotNull(added, "반환 ID로 같은 프로필 행을 다시 찾아야 한다");
                assertEquals(add.path("title").asText(), added.getTitle());
                assertNull(added.getRoleNote(), "짧은 서류 역할을 원문 역할 기록으로 저장하면 안 된다");
                assertNull(added.getReadme(), "서류 문장을 README 원문으로 저장하면 안 된다");

                ProfileData.Project original = projectFixture(STUDENT_ID);
                String changedTitle = marker();
                var patch = json.createObjectNode().put("title", changedTitle).put("periodText", "모의 8주")
                        .put("teamSize", 5).put("role", "백엔드").put("roleNote", "덮어쓰기 금지")
                        .put("readme", "덮어쓰기 금지").put("portfolioFileName", "변경금지.pdf")
                        .put("includeInApply", false);
                patch.putArray("techStack").add("Java").add("MySQL");
                patch.putArray("bullets").addObject().put("text", "모의 공고용 AI 문장");
                profiles.updateItem(STUDENT_ID, "PROJECTS", original.getItemId(), patch);
                ProfileData.Project updated = mapper.findProjectItem(STUDENT_ID, original.getItemId());
                assertEquals(changedTitle, updated.getTitle());
                assertEquals("모의 8주", updated.getPeriodText());
                assertEquals(5, updated.getTeamSize());
                assertEquals(patch.get("techStack"), updated.getTechStack());
                assertEquals(original.getReadme(), updated.getReadme());
                assertEquals(original.getRoleNote(), updated.getRoleNote());
                assertEquals(original.getPortfolioFileName(), updated.getPortfolioFileName());
                assertTrue(updated.isIncludeInApply(), "PROJECTS는 PORTFOLIO의 include 값을 변경하면 안 된다");
                assertEquals("USER", updated.getSource());
                String metadataHash = sourceHash();

                profiles.updateItem(STUDENT_ID, "PROJECTS", original.getItemId(),
                        json.createObjectNode().put("title", marker()));
                ProfileData.Project titleOnly = mapper.findProjectItem(STUDENT_ID, original.getItemId());
                assertEquals(updated.getTechStack(), titleOnly.getTechStack(), "누락한 techStack은 보존해야 한다");
                assertEquals("모의 8주", titleOnly.getPeriodText());
                assertEquals(5, titleOnly.getTeamSize());
                assertEquals(original.getReadme(), titleOnly.getReadme());
                assertEquals(original.getRoleNote(), titleOnly.getRoleNote());
                assertFalse(metadataHash.equals(sourceHash()));
                assertM8Shape(profiles.getProfile(STUDENT_ID));
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verify(aiClient, times(3)).extractProfile(any(JsonNode.class));
    }

    @Test
    void documentOriginalOnlyProjectEditLeavesRawRowsCompetencyAndHashUntouched() {
        Baseline baseline = baseline();
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project original = projectFixture(STUDENT_ID);
                List<ProjectRow> before = projectRows(STUDENT_ID);
                List<CompetencyRow> competenciesBefore = competencyRows();
                var patch = json.createObjectNode().put("role", "백엔드")
                        .put("roleNote", "서류의 긴 역할 값도 무시")
                        .put("readme", "서류에서 보낸 README도 무시");
                patch.putArray("bullets").addObject().put("text", "서류 전용 모의 문장");
                profiles.updateItem(STUDENT_ID, "PROJECTS", original.getItemId(), patch);
                assertTrue(before.equals(projectRows(STUDENT_ID)), "서류 전용 값만 바꾸면 원문 행을 보존해야 한다");
                assertTrue(competenciesBefore.equals(competencyRows()),
                        "프로필 변경이 없으면 역량 프로필·해시·시각을 변경하면 안 된다");
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verifyNoInteractions(aiClient);
    }

    @Test
    void portfolioIncludeUsesSameProjectIdAndPreservesFilenameAndOriginalsFromBothEntryPoints() {
        Baseline baseline = baseline();
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project original = projectFixture(STUDENT_ID);
                Long itemId = original.getItemId();
                StudentProfileResponse excluded = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PORTFOLIO", json.createObjectNode()
                                .put("include", false)));
                ProfileData.Project rawExcluded = mapper.findProjectItem(STUDENT_ID, itemId);
                assertFalse(rawExcluded.isIncludeInApply());
                assertEquals(original.getPortfolioFileName(), rawExcluded.getPortfolioFileName());
                assertEquals(original.getRoleNote(), rawExcluded.getRoleNote());
                assertEquals(original.getReadme(), rawExcluded.getReadme());
                assertStoredCompetency(excluded, expectedCompetency);
                assertM8Shape(excluded);
                String excludedHash = sourceHash();

                profiles.updateItem(STUDENT_ID, "PORTFOLIO", itemId,
                        json.createObjectNode().put("include", true).put("fileName", "여전히변경금지.pdf"));
                ProfileData.Project rawIncluded = mapper.findProjectItem(STUDENT_ID, itemId);
                assertTrue(rawIncluded.isIncludeInApply());
                assertEquals(original.getTitle(), rawIncluded.getTitle());
                assertEquals(original.getPortfolioFileName(), rawIncluded.getPortfolioFileName());
                assertEquals(original.getRoleNote(), rawIncluded.getRoleNote());
                assertEquals(original.getReadme(), rawIncluded.getReadme());
                assertEquals(original.getTechStack(), rawIncluded.getTechStack());
                assertFalse(excludedHash.equals(sourceHash()), "포트폴리오 포함 여부 변경도 해시에 반영해야 한다");
                assertStoredCompetency(profiles.getProfile(STUDENT_ID), expectedCompetency);
                assertM8Shape(profiles.getProfile(STUDENT_ID));
            });
        } finally {
            assertBaselineUnchanged(baseline);
        }
        verify(aiClient, times(2)).extractProfile(any(JsonNode.class));
    }

    @Test
    void anotherStudentsProjectAndPortfolioCannotBeChangedByEitherEntryPoint() {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        List<ProjectRow> otherRawBaseline = projectRows(2L);
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project foreign = projectFixture(2L);
                List<ProjectRow> otherWithFixture = projectRows(2L);
                Long foreignId = foreign.getItemId();
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, foreignId,
                                new ProfileItemPatchRequest("PROJECTS", json.createObjectNode().put("title", marker()))));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.deleteItem(STUDENT_ID, foreignId, "PROJECTS"));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, foreignId,
                                new ProfileItemPatchRequest("PORTFOLIO", json.createObjectNode().put("include", false))));
                assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "PROJECTS", foreignId,
                                json.createObjectNode().put("title", marker())));
                assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "PORTFOLIO", foreignId,
                                json.createObjectNode().put("include", false)));
                assertTrue(otherWithFixture.equals(projectRows(2L)), "다른 수료생의 원문 행을 보존해야 한다");
                assertTrue(baseline.projects().equals(projectRows(STUDENT_ID)));
                assertTrue(baseline.competencies().equals(competencyRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)));
            assertTrue(otherRawBaseline.equals(projectRows(2L)), "다른 수료생 원문도 롤백 후 같아야 한다");
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8_ADD", "M8_UPDATE", "M8_DELETE", "DOCUMENT_ADD", "DOCUMENT_UPDATE", "PORTFOLIO"})
    void aiFailureRollsBackProjectWritesFromBothEntryPointsIncludingRawOriginals(String operation) {
        Baseline baseline = baseline();
        StudentProfileResponse otherBaseline = profiles.getProfile(2L);
        String changedTitle = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    ProfileData.Project original = operation.endsWith("ADD") ? null : projectFixture(STUDENT_ID);
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        switch (operation) {
                            case "M8_ADD" -> service.addItem(STUDENT_ID, new ProfileItemRequest("PROJECTS",
                                    json.createObjectNode().put("title", changedTitle).put("readme", "모의 신규 원문")));
                            case "M8_UPDATE" -> service.updateItem(STUDENT_ID, original.getItemId(),
                                    new ProfileItemPatchRequest("PROJECTS", json.createObjectNode()
                                            .put("title", changedTitle).put("readme", "모의 변경 원문")
                                            .put("roleNote", "모의 변경 역할 기록")));
                            case "M8_DELETE" -> service.deleteItem(STUDENT_ID, original.getItemId(), "PROJECTS");
                            case "DOCUMENT_ADD" -> profiles.addItem(STUDENT_ID, "PROJECTS",
                                    json.createObjectNode().put("title", changedTitle));
                            case "DOCUMENT_UPDATE" -> profiles.updateItem(STUDENT_ID, "PROJECTS", original.getItemId(),
                                    json.createObjectNode().put("title", changedTitle).put("readme", "서류 덮어쓰기 금지"));
                            case "PORTFOLIO" -> profiles.updateItem(STUDENT_ID, "PORTFOLIO", original.getItemId(),
                                    json.createObjectNode().put("include", false));
                            default -> throw new IllegalArgumentException(operation);
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "각 진입 서비스가 AI 실패를 롤백 대상으로 표시해야 한다");
                    if (operation.endsWith("ADD")) {
                        assertEquals(1L, jdbc.queryForObject("""
                                SELECT COUNT(*) FROM student_project WHERE student_id = ? AND title = ?
                                """, Long.class, STUDENT_ID, changedTitle), "실제 INSERT 뒤 AI가 실패해야 한다");
                    } else if ("M8_DELETE".equals(operation)) {
                        assertNull(mapper.findProjectItem(STUDENT_ID, original.getItemId()),
                                "실제 DELETE 뒤 AI가 실패해야 한다");
                    } else {
                        ProfileData.Project changed = mapper.findProjectItem(STUDENT_ID, original.getItemId());
                        assertNotNull(changed);
                        if ("PORTFOLIO".equals(operation)) {
                            assertFalse(changed.isIncludeInApply());
                        } else {
                            assertEquals(changedTitle, changed.getTitle(), "실제 UPDATE 뒤 AI가 실패해야 한다");
                        }
                        if ("M8_UPDATE".equals(operation)) {
                            assertEquals("모의 변경 원문", changed.getReadme());
                            assertEquals("모의 변경 역할 기록", changed.getRoleNote());
                        } else {
                            assertEquals(original.getReadme(), changed.getReadme());
                            assertEquals(original.getRoleNote(), changed.getRoleNote());
                        }
                        assertEquals(original.getPortfolioFileName(), changed.getPortfolioFileName());
                    }
                    assertTrue(baseline.competencies().equals(competencyRows()),
                            "실패한 AI 결과로 기존 역량 프로필을 변경하면 안 된다");
                    assertTrue(otherBaseline.equals(profiles.getProfile(2L)));
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherBaseline.equals(profiles.getProfile(2L)));
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void m8BasicPatchPreservesOmittedValuesAndClearsOnlyExplicitOptionalNulls() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedStudentValues(STUDENT_ID);
        String name = marker();
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var patch = json.createObjectNode().put("name", name).put("email", "mock.basic@example.com")
                        .put("githubUrl", "github.com/mock-basic").put("desiredJob", "모의 백엔드 개발")
                        .put("preferredRegion", "모의 선호 지역")
                        .put("militaryStatus", "DO_NOT_WRITE").put("completedOn", "2000-01-01")
                        .put("courseId", 9999).put("studentId", 9999)
                        .put("lastResponseAt", "2000-01-01T00:00:00")
                        .put("createdAt", "2000-01-01T00:00:00")
                        .put("updatedAt", "2000-01-01T00:00:00");
                patch.putArray("skills").add("DO_NOT_WRITE");
                StudentProfileResponse changed = service.updateItem(STUDENT_ID, STUDENT_ID,
                        new ProfileItemPatchRequest("BASIC", patch));
                assertEquals(name, changed.basic().name());
                assertEquals("mock.basic@example.com", changed.basic().email());
                assertEquals("github.com/mock-basic", changed.basic().githubUrl());
                assertEquals("모의 백엔드 개발", changed.basic().desiredJob());
                assertEquals("모의 선호 지역", changed.basic().preferredRegion());
                assertTrue(protectedBefore.equals(protectedStudentValues(STUDENT_ID)),
                        "기본정보 수정으로 과정·병역·기술·수료일·응답시각을 바꾸면 안 된다");
                assertStoredCompetency(changed, expectedCompetency);
                assertM8Shape(changed);
                String firstHash = sourceHash();

                StudentProfileResponse partial = service.updateItem(STUDENT_ID, STUDENT_ID,
                        new ProfileItemPatchRequest("BASIC", json.createObjectNode()
                                .put("desiredJob", "모의 플랫폼 개발")));
                assertEquals(name, partial.basic().name());
                assertEquals("mock.basic@example.com", partial.basic().email());
                assertEquals("github.com/mock-basic", partial.basic().githubUrl());
                assertEquals("모의 플랫폼 개발", partial.basic().desiredJob());
                assertEquals("모의 선호 지역", partial.basic().preferredRegion());
                assertFalse(firstHash.equals(sourceHash()), "기본정보 변경은 AI 입력 해시에 반영해야 한다");
                assertStoredCompetency(partial, expectedCompetency);
                String partialHash = sourceHash();

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, STUDENT_ID,
                        new ProfileItemPatchRequest("BASIC", json.createObjectNode()
                                .putNull("email").putNull("githubUrl").putNull("desiredJob")
                                .putNull("preferredRegion")));
                assertEquals(name, cleared.basic().name());
                assertNull(cleared.basic().email());
                assertNull(cleared.basic().githubUrl());
                assertNull(cleared.basic().desiredJob());
                assertNull(cleared.basic().preferredRegion());
                assertFalse(partialHash.equals(sourceHash()));
                assertTrue(protectedBefore.equals(protectedStudentValues(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)), "다른 수료생의 기본정보를 보존해야 한다");
                assertStoredCompetency(cleared, expectedCompetency);
                assertM8Shape(cleared);
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
        }
        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient, times(3)).extractProfile(requests.capture());
        JsonNode basic = requests.getAllValues().get(0).get("basic");
        assertEquals(name, basic.path("name").asText());
        assertEquals("모의 백엔드 개발", basic.path("desiredJob").asText());
        assertEquals("모의 선호 지역", basic.path("preferredRegion").asText());
    }

    @Test
    void documentBasicPatchUsesSameSingletonAndPreservesUnrelatedStudentColumns() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedStudentValues(STUDENT_ID);
        String name = marker();
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var patch = json.createObjectNode().put("name", name).put("desiredJob", "모의 서비스 개발")
                        .put("preferredRegion", "모의 원격 근무").put("militaryStatus", "DO_NOT_WRITE")
                        .put("completedOn", "2000-01-01").put("courseId", 9999)
                        .put("lastResponseAt", "2000-01-01T00:00:00");
                patch.putArray("skills").add("DO_NOT_WRITE");

                profiles.updateItem(STUDENT_ID, "BASIC", STUDENT_ID, patch);

                StudentProfileResponse changed = profiles.getProfile(STUDENT_ID);
                assertEquals(name, changed.basic().name());
                assertEquals("모의 서비스 개발", changed.basic().desiredJob());
                assertEquals("모의 원격 근무", changed.basic().preferredRegion());
                assertTrue(java.util.Objects.equals(baseline.profile().basic().email(), changed.basic().email()),
                        "서류가 생략한 이메일은 보존해야 한다");
                assertTrue(java.util.Objects.equals(baseline.profile().basic().githubUrl(), changed.basic().githubUrl()));
                assertTrue(protectedBefore.equals(protectedStudentValues(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertStoredCompetency(changed, expectedCompetency);
                assertM8Shape(changed);
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
        }
        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient).extractProfile(request.capture());
        assertEquals(name, request.getValue().path("basic").path("name").asText());
        assertEquals("모의 서비스 개발", request.getValue().path("basic").path("desiredJob").asText());
    }

    @Test
    void basicSingletonMismatchAndMissingStudentCannotModifyEitherStudent() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        var patch = json.createObjectNode().put("name", marker());
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertThrows(ProfileItemNotFoundException.class, () -> service.updateItem(STUDENT_ID, 2L,
                        new ProfileItemPatchRequest("BASIC", patch)));
                BusinessException mismatch = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "BASIC", 2L, patch));
                assertEquals(ErrorCode.INVALID_REQUEST, mismatch.getErrorCode());
                BusinessException missing = assertThrows(BusinessException.class,
                        () -> service.updateItem(Long.MAX_VALUE, Long.MAX_VALUE,
                                new ProfileItemPatchRequest("BASIC", patch)));
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missing.getErrorCode());
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(baseline.competencies().equals(competencyRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8", "DOCUMENT"})
    void aiFailureRollsBackBasicFromBothEntryPointsIncludingStudentTimestamps(String entryPoint) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedStudentValues(STUDENT_ID);
        String name = marker();
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    var patch = json.createObjectNode().put("name", name)
                            .put("email", "mock.rollback@example.com").put("desiredJob", "모의 실패 검증");
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("M8".equals(entryPoint)) {
                            service.updateItem(STUDENT_ID, STUDENT_ID, new ProfileItemPatchRequest("BASIC", patch));
                        } else {
                            profiles.updateItem(STUDENT_ID, "BASIC", STUDENT_ID, patch);
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "AI 실패가 같은 트랜잭션을 롤백 대상으로 표시해야 한다");
                    assertEquals(name, jdbc.queryForObject("SELECT name FROM student WHERE id = ?",
                            String.class, STUDENT_ID), "실제 기본정보 UPDATE 뒤 AI가 실패해야 한다");
                    assertTrue(protectedBefore.equals(protectedStudentValues(STUDENT_ID)),
                            "실패 경로에서도 상태 판정용 응답시각과 등록 정보를 보호해야 한다");
                    assertTrue(baseline.competencies().equals(competencyRows()));
                    assertTrue(otherStudent.equals(studentRows(2L)));
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8", "DOCUMENT"})
    void militaryPatchStoresAllSupportedValuesAndExplicitNullWithoutChangingOtherStudentFields(String entryPoint) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedMilitaryStudentValues(STUDENT_ID);
        List<Map<String, Object>> statusesBefore = militaryStudentStatusRows();
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));
        List<String> values = List.of("SERVED", "NOT_SERVED", "EXEMPT", "NOT_APPLICABLE", "CLEAR");

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                String previousHash = null;
                for (String value : values) {
                    String expected = "CLEAR".equals(value) ? null : value;
                    var patch = json.createObjectNode().put("name", marker())
                            .put("email", "mock.military@example.com").put("githubUrl", "github.com/mock-military")
                            .put("desiredJob", "덮어쓰기 금지 모의 직무").put("preferredRegion", "덮어쓰기 금지 모의 지역")
                            .put("militaryStatus", "DO_NOT_WRITE").put("courseId", 9999)
                            .put("completedOn", "2000-01-01").put("studentId", 9999)
                            .put("lastResponseAt", "2000-01-01T00:00:00")
                            .put("updatedAt", "2000-01-01T00:00:00").put("status", "EMPLOYED");
                    patch.putArray("skills").add("DO_NOT_WRITE");
                    if (expected == null) patch.putNull("value");
                    else patch.put("value", expected);

                    StudentProfileResponse changed;
                    if ("M8".equals(entryPoint)) {
                        changed = service.updateItem(STUDENT_ID, STUDENT_ID,
                                new ProfileItemPatchRequest("MILITARY", patch));
                    } else {
                        profiles.updateItem(STUDENT_ID, "MILITARY", STUDENT_ID, patch);
                        changed = profiles.getProfile(STUDENT_ID);
                    }

                    assertEquals(expected, changed.basic().militaryStatus());
                    assertEquals(expected, jdbc.queryForObject(
                            "SELECT military_status FROM student WHERE id = ?", String.class, STUDENT_ID));
                    assertTrue(protectedBefore.equals(protectedMilitaryStudentValues(STUDENT_ID)),
                            "병역 수정으로 기본정보·기술·수료일·과정·응답시각을 바꾸면 안 된다");
                    assertTrue(statusesBefore.equals(militaryStudentStatusRows()),
                            "병역 수정은 상태 판정 테이블을 변경하면 안 된다");
                    assertTrue(otherStudent.equals(studentRows(2L)), "다른 수료생 자료를 보존해야 한다");
                    assertStoredCompetency(changed, expectedCompetency);
                    assertM8Shape(changed);
                    String changedHash = sourceHash();
                    if (previousHash != null) {
                        assertFalse(previousHash.equals(changedHash), "병역 값 변경은 AI 입력 해시에 반영해야 한다");
                    }
                    previousHash = changedHash;
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(militaryStudentStatusRows()));
        }
        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient, times(values.size())).extractProfile(requests.capture());
        for (int index = 0; index < values.size(); index++) {
            JsonNode military = requests.getAllValues().get(index).path("basic").get("militaryStatus");
            assertNotNull(military, "AI 입력에서도 비어 있는 병역 키를 유지해야 한다");
            if ("CLEAR".equals(values.get(index))) assertTrue(military.isNull());
            else assertEquals(values.get(index), military.asText());
        }
    }

    @Test
    void militaryInvalidEnumMissingStudentAndWrongSingletonCannotWriteOrExtract() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<Map<String, Object>> statusesBefore = militaryStudentStatusRows();
        var valid = json.createObjectNode().put("value", "SERVED");
        var invalid = json.createObjectNode().put("value", "served");
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, 2L, new ProfileItemPatchRequest("MILITARY", valid)));
                BusinessException wrongDocumentId = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "MILITARY", 2L, valid));
                assertEquals(ErrorCode.INVALID_REQUEST, wrongDocumentId.getErrorCode());
                BusinessException invalidM8Value = assertThrows(BusinessException.class,
                        () -> service.updateItem(STUDENT_ID, STUDENT_ID,
                                new ProfileItemPatchRequest("MILITARY", invalid)));
                assertEquals(ErrorCode.INVALID_REQUEST, invalidM8Value.getErrorCode());
                BusinessException invalidDocumentValue = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "MILITARY", STUDENT_ID, invalid));
                assertEquals(ErrorCode.INVALID_REQUEST, invalidDocumentValue.getErrorCode());
                BusinessException missingM8Student = assertThrows(BusinessException.class,
                        () -> service.updateItem(Long.MAX_VALUE, Long.MAX_VALUE,
                                new ProfileItemPatchRequest("MILITARY", valid)));
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missingM8Student.getErrorCode());
                BusinessException missingDocumentStudent = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(Long.MAX_VALUE, "MILITARY", Long.MAX_VALUE, valid));
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missingDocumentStudent.getErrorCode());
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(baseline.competencies().equals(competencyRows()));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(statusesBefore.equals(militaryStudentStatusRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(militaryStudentStatusRows()));
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8", "DOCUMENT"})
    void aiFailureRollsBackMilitaryFromBothEntryPointsIncludingStudentTimestamps(String entryPoint) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedMilitaryStudentValues(STUDENT_ID);
        List<Map<String, Object>> statusesBefore = militaryStudentStatusRows();
        String originalValue = baseline.profile().basic().militaryStatus();
        String changedValue = "SERVED".equals(originalValue) ? "EXEMPT" : "SERVED";
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    var patch = json.createObjectNode().put("value", changedValue);
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("M8".equals(entryPoint)) {
                            service.updateItem(STUDENT_ID, STUDENT_ID, new ProfileItemPatchRequest("MILITARY", patch));
                        } else {
                            profiles.updateItem(STUDENT_ID, "MILITARY", STUDENT_ID, patch);
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "AI 실패가 병역 변경과 같은 트랜잭션을 롤백해야 한다");
                    assertEquals(changedValue, jdbc.queryForObject(
                            "SELECT military_status FROM student WHERE id = ?", String.class, STUDENT_ID),
                            "실제 병역 UPDATE 뒤에 AI가 실패해야 한다");
                    assertTrue(protectedBefore.equals(protectedMilitaryStudentValues(STUDENT_ID)));
                    assertTrue(baseline.competencies().equals(competencyRows()));
                    assertTrue(otherStudent.equals(studentRows(2L)));
                    assertTrue(statusesBefore.equals(militaryStudentStatusRows()));
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(militaryStudentStatusRows()));
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8", "DOCUMENT"})
    void skillsPatchReplacesAndClearsJsonArrayWhilePreservingOrderTextAndOtherStudentFields(String entryPoint) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedSkillsStudentValues(STUDENT_ID);
        List<Map<String, Object>> statusesBefore = skillsStudentStatusRows();
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));
        List<JsonNode> values = List.of(
                json.createArrayNode().add("Java").add(" 모의 기술 ")
                        .add("모의 \"인용\" 기술\\도구\n설명").add("Java"),
                json.createArrayNode());

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                String previousHash = null;
                for (JsonNode tags : values) {
                    var patch = json.createObjectNode().put("source", "AI").put("name", marker())
                            .put("email", "mock.skills@example.com").put("githubUrl", "github.com/mock-skills")
                            .put("desiredJob", "덮어쓰기 금지 모의 직무").put("preferredRegion", "덮어쓰기 금지 모의 지역")
                            .put("militaryStatus", "DO_NOT_WRITE").put("studentId", 9999).put("courseId", 9999)
                            .put("completedOn", "2000-01-01").put("lastResponseAt", "2000-01-01T00:00:00")
                            .put("updatedAt", "2000-01-01T00:00:00").put("status", "EMPLOYED");
                    patch.set("tags", tags);
                    patch.putArray("summary").addObject().put("text", "프로필 기술 원문에 저장하면 안 되는 모의 AI 문장");
                    patch.putArray("skills").add("DO_NOT_WRITE");
                    StudentProfileResponse changed;
                    if ("M8".equals(entryPoint)) {
                        changed = service.updateItem(STUDENT_ID, STUDENT_ID,
                                new ProfileItemPatchRequest("SKILLS", patch));
                    } else {
                        profiles.updateItem(STUDENT_ID, "SKILLS", STUDENT_ID, patch);
                        changed = profiles.getProfile(STUDENT_ID);
                    }

                    assertEquals(tags, json.valueToTree(changed.skills()),
                            "기술 목록은 순서·중복·공백·특수문자를 바꾸지 않고 전체 교체해야 한다");
                    assertEquals(tags, mapper.findStudent(STUDENT_ID).getSkills(),
                            "JSON TypeHandler가 실제 저장한 기술 배열을 그대로 읽어야 한다");
                    assertTrue(protectedBefore.equals(protectedSkillsStudentValues(STUDENT_ID)),
                            "기술 수정은 기본정보·병역·수료일·과정·응답시각을 보존해야 한다");
                    assertTrue(statusesBefore.equals(skillsStudentStatusRows()),
                            "기술 수정은 상태 판정 결과를 변경하면 안 된다");
                    assertTrue(otherStudent.equals(studentRows(2L)), "다른 수료생의 기술 목록을 보존해야 한다");
                    assertStoredCompetency(changed, expectedCompetency);
                    assertM8Shape(changed);
                    String changedHash = sourceHash();
                    if (previousHash != null) {
                        assertFalse(previousHash.equals(changedHash), "기술 배열 변경은 AI 입력 해시에 반영해야 한다");
                    }
                    previousHash = changedHash;
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
        }
        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient, times(values.size())).extractProfile(requests.capture());
        for (int index = 0; index < values.size(); index++) {
            assertEquals(values.get(index), requests.getAllValues().get(index).get("skills"),
                    "AI에는 서류 요약 대신 저장한 기술 원문 배열을 보내야 한다");
        }
    }

    @Test
    void documentSkillsSummaryOnlyPatchLeavesStudentAndCompetencyUntouchedWithoutExtracting() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<Map<String, Object>> statusesBefore = skillsStudentStatusRows();
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                var patch = json.createObjectNode().put("source", "AI");
                patch.putArray("summary").addObject().put("text", "서류에만 남기는 모의 기술 요약")
                        .put("sentenceId", "mock-skills-1");

                profiles.updateItem(STUDENT_ID, "SKILLS", STUDENT_ID, patch);

                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)),
                        "요약만 바꾸면 기술 원문과 학생 updated_at도 보존해야 한다");
                assertTrue(baseline.competencies().equals(competencyRows()),
                        "프로필 변경이 없으면 역량 프로필·해시·추출 시각을 보존해야 한다");
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
        }
        verifyNoInteractions(aiClient);
    }

    @Test
    void skillsMalformedArrayMissingStudentAndWrongSingletonCannotWriteOrExtract() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<Map<String, Object>> statusesBefore = skillsStudentStatusRows();
        var valid = json.createObjectNode();
        valid.putArray("tags").add("모의 기술");
        List<JsonNode> invalidValues = List.of(
                json.createObjectNode().putNull("tags"),
                json.createObjectNode().put("tags", "Java"),
                json.createObjectNode().set("tags", json.createArrayNode().add(42)),
                json.createObjectNode().set("tags", json.createArrayNode().add(" ")));
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, 2L, new ProfileItemPatchRequest("SKILLS", valid)));
                BusinessException wrongDocumentId = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "SKILLS", 2L, valid));
                assertEquals(ErrorCode.INVALID_REQUEST, wrongDocumentId.getErrorCode());
                for (JsonNode invalid : invalidValues) {
                    BusinessException invalidM8 = assertThrows(BusinessException.class,
                            () -> service.updateItem(STUDENT_ID, STUDENT_ID,
                                    new ProfileItemPatchRequest("SKILLS", invalid)));
                    BusinessException invalidDocument = assertThrows(BusinessException.class,
                            () -> profiles.updateItem(STUDENT_ID, "SKILLS", STUDENT_ID, invalid));
                    assertEquals(ErrorCode.INVALID_REQUEST, invalidM8.getErrorCode());
                    assertEquals(ErrorCode.INVALID_REQUEST, invalidDocument.getErrorCode());
                }
                var summaryOnly = json.createObjectNode().put("source", "AI");
                summaryOnly.putArray("summary");
                BusinessException missingTags = assertThrows(BusinessException.class,
                        () -> service.updateItem(STUDENT_ID, STUDENT_ID,
                                new ProfileItemPatchRequest("SKILLS", summaryOnly)));
                assertEquals(ErrorCode.INVALID_REQUEST, missingTags.getErrorCode());
                BusinessException wrongSummaryId = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(STUDENT_ID, "SKILLS", 2L, summaryOnly));
                assertEquals(ErrorCode.INVALID_REQUEST, wrongSummaryId.getErrorCode());
                BusinessException missingM8Student = assertThrows(BusinessException.class,
                        () -> service.updateItem(Long.MAX_VALUE, Long.MAX_VALUE,
                                new ProfileItemPatchRequest("SKILLS", valid)));
                BusinessException missingDocumentStudent = assertThrows(BusinessException.class,
                        () -> profiles.updateItem(Long.MAX_VALUE, "SKILLS", Long.MAX_VALUE, summaryOnly));
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missingM8Student.getErrorCode());
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missingDocumentStudent.getErrorCode());
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(baseline.competencies().equals(competencyRows()));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M8", "DOCUMENT"})
    void aiFailureRollsBackSkillsFromBothEntryPointsIncludingStudentTimestamps(String entryPoint) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        Map<String, Object> protectedBefore = protectedSkillsStudentValues(STUDENT_ID);
        List<Map<String, Object>> statusesBefore = skillsStudentStatusRows();
        JsonNode tags = json.createArrayNode().add(marker());
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    var patch = json.createObjectNode().set("tags", tags);
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("M8".equals(entryPoint)) {
                            service.updateItem(STUDENT_ID, STUDENT_ID, new ProfileItemPatchRequest("SKILLS", patch));
                        } else {
                            profiles.updateItem(STUDENT_ID, "SKILLS", STUDENT_ID, patch);
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "AI 실패는 기술 원문 저장도 같은 트랜잭션에서 롤백해야 한다");
                    assertEquals(tags, mapper.findStudent(STUDENT_ID).getSkills(),
                            "실제 기술 배열 UPDATE 뒤에 AI가 실패해야 한다");
                    assertTrue(protectedBefore.equals(protectedSkillsStudentValues(STUDENT_ID)));
                    assertTrue(baseline.competencies().equals(competencyRows()));
                    assertTrue(otherStudent.equals(studentRows(2L)));
                    assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(statusesBefore.equals(skillsStudentStatusRows()));
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    @Test
    void m8PortfolioCanAttachRenameClearAndDeleteWithoutDeletingProjectOriginals() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<ProjectRow> otherProjects = projectRows(2L);
        Long[] createdId = {null};
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));

        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project original = projectFixture(STUDENT_ID);
                Long itemId = original.getItemId();
                createdId[0] = itemId;
                jdbc.update("UPDATE student_project SET portfolio_file_url = NULL WHERE student_id = ? AND id = ?",
                        STUDENT_ID, itemId);

                StudentProfileResponse attached = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PORTFOLIO", json.createObjectNode()
                                .put("fileName", "모의_신규_포트폴리오.pdf").put("include", false)
                                .put("title", "덮어쓰기 금지").put("readme", "덮어쓰기 금지")
                                .put("roleNote", "덮어쓰기 금지")));
                ProfileData.Project rawAttached = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals("모의_신규_포트폴리오.pdf", rawAttached.getPortfolioFileName());
                assertFalse(rawAttached.isIncludeInApply());
                assertPortfolioOriginalsUnchanged(original, rawAttached);
                assertStoredCompetency(attached, expectedCompetency);
                assertM8Shape(attached);
                String attachedHash = sourceHash();

                StudentProfileResponse renamed = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PORTFOLIO", json.createObjectNode()
                                .put("fileName", "모의_수정_포트폴리오.pdf")));
                ProfileData.Project rawRenamed = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals("모의_수정_포트폴리오.pdf", rawRenamed.getPortfolioFileName());
                assertFalse(rawRenamed.isIncludeInApply(), "파일명만 보내면 기존 포함 여부를 보존해야 한다");
                assertPortfolioOriginalsUnchanged(original, rawRenamed);
                assertStoredCompetency(renamed, expectedCompetency);
                assertFalse(attachedHash.equals(sourceHash()));
                String renamedHash = sourceHash();

                service.updateItem(STUDENT_ID, itemId, new ProfileItemPatchRequest("PORTFOLIO",
                        json.createObjectNode().put("include", true)));
                ProfileData.Project rawIncluded = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals("모의_수정_포트폴리오.pdf", rawIncluded.getPortfolioFileName());
                assertTrue(rawIncluded.isIncludeInApply());
                assertPortfolioOriginalsUnchanged(original, rawIncluded);
                assertFalse(renamedHash.equals(sourceHash()));
                String includedHash = sourceHash();

                StudentProfileResponse cleared = service.updateItem(STUDENT_ID, itemId,
                        new ProfileItemPatchRequest("PORTFOLIO", json.createObjectNode().putNull("fileName")));
                ProfileData.Project rawCleared = mapper.findProjectItem(STUDENT_ID, itemId);
                assertNull(rawCleared.getPortfolioFileName());
                assertTrue(rawCleared.isIncludeInApply(), "명시한 파일명 null만 비우고 누락한 포함 여부는 보존해야 한다");
                assertPortfolioOriginalsUnchanged(original, rawCleared);
                assertStoredCompetency(cleared, expectedCompetency);
                assertM8Shape(cleared);
                assertFalse(includedHash.equals(sourceHash()));

                service.updateItem(STUDENT_ID, itemId, new ProfileItemPatchRequest("PORTFOLIO",
                        json.createObjectNode().put("fileName", "모의_삭제_검증.pdf")));
                String beforeDeleteHash = sourceHash();
                StudentProfileResponse removed = service.deleteItem(STUDENT_ID, itemId, "PORTFOLIO");
                ProfileData.Project rawRemoved = mapper.findProjectItem(STUDENT_ID, itemId);
                assertNotNull(rawRemoved, "포트폴리오 삭제는 프로젝트 행을 삭제하면 안 된다");
                assertNull(rawRemoved.getPortfolioFileName());
                assertFalse(rawRemoved.isIncludeInApply());
                assertPortfolioOriginalsUnchanged(original, rawRemoved);
                assertTrue(removed.projects().stream().anyMatch(project -> itemId.equals(project.itemId())));
                assertStoredCompetency(removed, expectedCompetency);
                assertM8Shape(removed);
                assertFalse(beforeDeleteHash.equals(sourceHash()));
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(otherProjects.equals(projectRows(2L)));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(otherProjects.equals(projectRows(2L)));
        }
        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(aiClient, times(6)).extractProfile(requests.capture());
        JsonNode attachedInput = portfolioProjectInput(requests.getAllValues().get(0), createdId[0]);
        assertEquals("모의_신규_포트폴리오.pdf", attachedInput.path("portfolioFileName").asText());
        assertFalse(attachedInput.path("includeInApply").asBoolean());
        JsonNode removedInput = portfolioProjectInput(requests.getAllValues().get(5), createdId[0]);
        assertTrue(removedInput.has("portfolioFileName"));
        assertTrue(removedInput.get("portfolioFileName").isNull());
        assertFalse(removedInput.path("includeInApply").asBoolean());
        assertEquals(attachedInput.get("readme"), removedInput.get("readme"));
        assertEquals(attachedInput.get("roleNote"), removedInput.get("roleNote"));
    }

    @Test
    void documentPortfolioStillChangesOnlyIncludeAfterM8RegistersFilename() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<ProjectRow> otherProjects = projectRows(2L);
        MockAiClient fixture = new MockAiClient(json, null);
        JsonNode expectedCompetency = fixture.extractProfile(json.createObjectNode()).get("profile");
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenAnswer(invocation -> fixture.extractProfile(invocation.getArgument(0)));
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project original = projectFixture(STUDENT_ID);
                Long itemId = original.getItemId();
                service.updateItem(STUDENT_ID, itemId, new ProfileItemPatchRequest("PORTFOLIO",
                        json.createObjectNode().put("fileName", "모의_M8_등록.pdf")));
                assertEquals("모의_M8_등록.pdf", mapper.findProjectItem(STUDENT_ID, itemId).getPortfolioFileName());

                profiles.updateItem(STUDENT_ID, "PORTFOLIO", itemId, json.createObjectNode()
                        .put("include", false).put("fileName", "서류에서_덮어쓰기_금지.pdf")
                        .put("title", "덮어쓰기 금지").put("readme", "덮어쓰기 금지")
                        .put("roleNote", "덮어쓰기 금지"));

                ProfileData.Project changed = mapper.findProjectItem(STUDENT_ID, itemId);
                assertEquals("모의_M8_등록.pdf", changed.getPortfolioFileName());
                assertFalse(changed.isIncludeInApply());
                assertPortfolioOriginalsUnchanged(original, changed);
                assertStoredCompetency(profiles.getProfile(STUDENT_ID), expectedCompetency);
                assertM8Shape(profiles.getProfile(STUDENT_ID));
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(otherProjects.equals(projectRows(2L)));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(otherProjects.equals(projectRows(2L)));
        }
        verify(aiClient, times(2)).extractProfile(any(JsonNode.class));
    }

    @Test
    void portfolioMalformedFilenameAndForeignOrMissingProjectCannotWriteOrExtract() {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<ProjectRow> otherProjects = projectRows(2L);
        try {
            transactions.executeWithoutResult(status -> {
                status.setRollbackOnly();
                ProfileData.Project own = projectFixture(STUDENT_ID);
                ProfileData.Project foreign = projectFixture(2L);
                List<ProjectRow> ownWithFixture = projectRows(STUDENT_ID);
                List<ProjectRow> otherWithFixture = projectRows(2L);
                for (JsonNode invalid : List.of(json.nullNode(), json.createArrayNode(), json.createObjectNode(),
                        json.createObjectNode().put("fileName", " "),
                        json.createObjectNode().put("fileName", "a".repeat(301)),
                        json.createObjectNode().put("fileName", 42),
                        json.createObjectNode().put("fileName", "모의.pdf").putNull("include"),
                        json.createObjectNode().put("title", "포트폴리오 변경 필드 아님"))) {
                    BusinessException failure = assertThrows(BusinessException.class,
                            () -> service.updateItem(STUDENT_ID, own.getItemId(),
                                    new ProfileItemPatchRequest("PORTFOLIO", invalid)));
                    assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
                }
                var valid = json.createObjectNode().put("fileName", "모의_차단.pdf");
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, foreign.getItemId(),
                                new ProfileItemPatchRequest("PORTFOLIO", valid)));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.deleteItem(STUDENT_ID, foreign.getItemId(), "PORTFOLIO"));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.updateItem(STUDENT_ID, Long.MAX_VALUE,
                                new ProfileItemPatchRequest("PORTFOLIO", valid)));
                assertThrows(ProfileItemNotFoundException.class,
                        () -> service.deleteItem(STUDENT_ID, Long.MAX_VALUE, "PORTFOLIO"));
                BusinessException missingStudent = assertThrows(BusinessException.class,
                        () -> service.updateItem(Long.MAX_VALUE, Long.MAX_VALUE,
                                new ProfileItemPatchRequest("PORTFOLIO", valid)));
                assertEquals(ErrorCode.STUDENT_NOT_FOUND, missingStudent.getErrorCode());
                assertTrue(ownWithFixture.equals(projectRows(STUDENT_ID)));
                assertTrue(otherWithFixture.equals(projectRows(2L)));
                assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                assertTrue(otherStudent.equals(studentRows(2L)));
                assertTrue(baseline.competencies().equals(competencyRows()));
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(otherProjects.equals(projectRows(2L)));
        }
        verifyNoInteractions(aiClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "DELETE"})
    void aiFailureRollsBackPortfolioFilenameOrRemovalWithoutLosingProject(String operation) {
        Baseline baseline = baseline();
        List<Map<String, Object>> otherStudent = studentRows(2L);
        List<ProjectRow> otherProjects = projectRows(2L);
        when(aiClient.extractProfile(any(JsonNode.class)))
                .thenThrow(new BusinessException(ErrorCode.AI_SERVER_ERROR));
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    assertFalse(status.isRollbackOnly());
                    ProfileData.Project original = projectFixture(STUDENT_ID);
                    Long itemId = original.getItemId();
                    BusinessException failure = assertThrows(BusinessException.class, () -> {
                        if ("UPDATE".equals(operation)) {
                            service.updateItem(STUDENT_ID, itemId, new ProfileItemPatchRequest("PORTFOLIO",
                                    json.createObjectNode().put("fileName", "모의_실패_검증.pdf").put("include", false)));
                        } else {
                            service.deleteItem(STUDENT_ID, itemId, "PORTFOLIO");
                        }
                    });
                    assertEquals(ErrorCode.AI_SERVER_ERROR, failure.getErrorCode());
                    assertTrue(status.isRollbackOnly(), "AI 실패가 첨부 변경도 같은 트랜잭션에서 롤백해야 한다");
                    ProfileData.Project changed = mapper.findProjectItem(STUDENT_ID, itemId);
                    assertPortfolioOriginalsUnchanged(original, changed);
                    assertFalse(changed.isIncludeInApply());
                    if ("UPDATE".equals(operation)) {
                        assertEquals("모의_실패_검증.pdf", changed.getPortfolioFileName());
                    } else {
                        assertNull(changed.getPortfolioFileName());
                    }
                    assertTrue(baseline.competencies().equals(competencyRows()));
                    assertTrue(baseline.students().equals(studentRows(STUDENT_ID)));
                    assertTrue(otherStudent.equals(studentRows(2L)));
                    assertTrue(otherProjects.equals(projectRows(2L)));
                } finally {
                    status.setRollbackOnly();
                }
            });
        } finally {
            assertBaselineUnchanged(baseline);
            assertTrue(otherStudent.equals(studentRows(2L)));
            assertTrue(otherProjects.equals(projectRows(2L)));
        }
        verify(aiClient).extractProfile(any(JsonNode.class));
    }

    private void assertPortfolioOriginalsUnchanged(ProfileData.Project original, ProfileData.Project changed) {
        assertNotNull(changed, "첨부를 수정하거나 삭제해도 프로젝트는 남아 있어야 한다");
        assertEquals(original.getItemId(), changed.getItemId());
        assertEquals(original.getTitle(), changed.getTitle());
        assertEquals(original.getPeriodText(), changed.getPeriodText());
        assertEquals(original.getTeamSize(), changed.getTeamSize());
        assertEquals(original.getTechStack(), changed.getTechStack());
        assertEquals(original.getReadme(), changed.getReadme());
        assertEquals(original.getRoleNote(), changed.getRoleNote());
        assertEquals("USER", changed.getSource());
    }

    private JsonNode portfolioProjectInput(JsonNode request, Long itemId) {
        for (JsonNode project : request.path("projects")) {
            if (project.path("itemId").asLong() == itemId) return project;
        }
        throw new AssertionError("AI 입력에 변경한 프로젝트 행이 있어야 한다");
    }

    private Map<String, Object> protectedSkillsStudentValues(Long studentId) {
        var protectedValues = new HashMap<>(studentRows(studentId).get(0));
        protectedValues.remove("skills");
        protectedValues.remove("updated_at");
        return protectedValues;
    }

    private List<Map<String, Object>> skillsStudentStatusRows() {
        return jdbc.queryForList("SELECT * FROM student_status WHERE student_id IN (?, ?) ORDER BY student_id",
                STUDENT_ID, 2L);
    }

    private Map<String, Object> protectedMilitaryStudentValues(Long studentId) {
        var protectedValues = new HashMap<>(studentRows(studentId).get(0));
        protectedValues.remove("military_status");
        protectedValues.remove("updated_at");
        return protectedValues;
    }

    private List<Map<String, Object>> militaryStudentStatusRows() {
        return jdbc.queryForList("SELECT * FROM student_status WHERE student_id IN (?, ?) ORDER BY student_id",
                STUDENT_ID, 2L);
    }

    private List<Map<String, Object>> studentRows(Long studentId) {
        return jdbc.queryForList("SELECT * FROM student WHERE id = ?", studentId);
    }

    private Map<String, Object> protectedStudentValues(Long studentId) {
        var protectedValues = new HashMap<>(studentRows(studentId).get(0));
        for (String field : List.of("name", "email", "github_url", "desired_job", "preferred_region", "updated_at")) {
            protectedValues.remove(field);
        }
        return protectedValues;
    }

    private ProfileData.Project projectFixture(Long studentId) {
        assertEquals(studentId, mapper.lockStudent(studentId));
        var project = new ProfileData.Project();
        project.setTitle(marker());
        project.setPeriodText("모의 6주");
        project.setTeamSize(4);
        project.setTechStack(json.createArrayNode().add("Java"));
        project.setRoleNote("모의 프로젝트에서 API 설계와 검증을 맡은 긴 역할 기록");
        project.setReadme("# 모의 README\nAI 근거가 인용하는 원문을 보존합니다.");
        project.setIncludeInApply(true);
        assertEquals(1, mapper.insertProject(studentId, project));
        assertNotNull(project.getItemId(), "MyBatis가 생성한 프로젝트 ID가 있어야 한다");
        jdbc.update("""
                UPDATE student_project SET source = 'REGISTERED', portfolio_file_url = ?
                WHERE student_id = ? AND id = ?
                """, "모의_프로젝트.pdf", studentId, project.getItemId());
        ProfileData.Project stored = mapper.findProjectItem(studentId, project.getItemId());
        assertNotNull(stored);
        return stored;
    }

    private List<ProjectRow> projectRows(Long studentId) {
        return jdbc.query("""
                SELECT id, title, period_text, team_size, tech_stack, role_note, readme,
                       portfolio_file_url, include_in_apply, source
                FROM student_project WHERE student_id = ? ORDER BY id
                """, (rs, index) -> new ProjectRow(rs.getLong("id"), rs.getString("title"),
                rs.getString("period_text"), rs.getObject("team_size", Integer.class), rs.getString("tech_stack"),
                rs.getString("role_note"), rs.getString("readme"), rs.getString("portfolio_file_url"),
                rs.getBoolean("include_in_apply"), rs.getString("source")), studentId);
    }

    private List<CoverLetterRow> coverLetterRows() {
        return jdbc.query("SELECT content, updated_at FROM student_cover_letter WHERE student_id = ?",
                (rs, index) -> new CoverLetterRow(rs.getString("content"),
                        rs.getTimestamp("updated_at").toLocalDateTime()), STUDENT_ID);
    }

    private Baseline baseline() {
        return new Baseline(profiles.getProfile(STUDENT_ID), competencyRows(), projectRows(STUDENT_ID),
                studentRows(STUDENT_ID));
    }

    private void assertBaselineUnchanged(Baseline expected) {
        // assertEquals의 실패 메시지에 등록 자료가 출력되지 않도록 전체 객체는 비교식만 전달한다.
        assertTrue(expected.profile().equals(profiles.getProfile(STUDENT_ID)),
                "롤백 후 기존 M8 프로필과 항목 목록이 같아야 한다");
        assertTrue(expected.competencies().equals(competencyRows()),
                "롤백 후 기존 역량 프로필·해시·추출 시각이 같아야 한다");
        assertTrue(expected.projects().equals(projectRows(STUDENT_ID)),
                "롤백 후 기존 프로젝트의 README·역할 기록·파일명도 같아야 한다");
        assertTrue(expected.students().equals(studentRows(STUDENT_ID)),
                "롤백 후 수료생 원문과 last_response_at·updated_at 등 전체 컬럼이 같아야 한다");
    }

    private List<CompetencyRow> competencyRows() {
        return jdbc.query("""
                SELECT profile, source_hash, extracted_at FROM competency_profile WHERE student_id = ?
                """, (rs, index) -> new CompetencyRow(
                rs.getString("profile"), rs.getString("source_hash"),
                rs.getTimestamp("extracted_at").toLocalDateTime()), STUDENT_ID);
    }

    private long competencyCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM competency_profile WHERE student_id = ?",
                Long.class, STUDENT_ID);
    }

    private String sourceHash() {
        return jdbc.queryForObject("SELECT source_hash FROM competency_profile WHERE student_id = ?",
                String.class, STUDENT_ID);
    }

    private void assertStoredCompetency(StudentProfileResponse response, JsonNode expected) {
        assertTrue(expected.equals(response.competencyProfile()), "M8에 새 역량 프로필을 반환해야 한다");
        assertTrue(expected.equals(mapper.findStudent(STUDENT_ID).getCompetencyProfile()),
                "JSON TypeHandler로 저장한 역량 프로필을 다시 읽을 수 있어야 한다");
        assertNotNull(response.profileUpdatedAt());
        assertEquals(ZoneOffset.ofHours(9), response.profileUpdatedAt().getOffset());
    }

    private void assertM8Shape(StudentProfileResponse response) {
        JsonNode tree = json.valueToTree(response);
        assertEquals(Set.of("studentId", "profileUpdatedAt", "basic", "course", "education", "careers",
                "certificates", "skills", "projects", "coverLetter", "competencyProfile"), fields(tree));
        assertEquals(Set.of("name", "email", "githubUrl", "desiredJob", "preferredRegion",
                "militaryStatus", "completedOn"), fields(tree.get("basic")),
                "기본정보 수정도 기존 M8의 7개 필드를 유지해야 한다");
        tree.get("education").forEach(education -> assertEquals(Set.of(
                "itemId", "source", "schoolName", "major", "degree", "status", "graduatedOn"),
                fields(education), "학력 응답은 기존 M8 형식을 유지해야 한다"));
        tree.get("careers").forEach(career -> assertEquals(Set.of(
                "itemId", "source", "companyName", "position", "startedOn", "endedOn", "description"),
                fields(career), "경력 응답은 기존 M8 형식을 유지해야 한다"));
        tree.get("projects").forEach(project -> assertEquals(Set.of(
                "itemId", "title", "periodText", "teamSize", "techStack", "portfolioFileName", "includeInApply"),
                fields(project), "AI용 원문을 기존 M8 프로젝트 응답에 추가하면 안 된다"));
    }

    private JsonNode competency(String certificateName) {
        var profile = json.createObjectNode();
        profile.putArray("roles").add("모의 역할");
        profile.putArray("skills");
        profile.putArray("achievements");
        profile.putArray("certificates").add(certificateName);
        profile.putNull("desiredJob");
        profile.putNull("preferredRegion");
        return profile;
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static StudentProfileResponse.Certificate findCertificate(StudentProfileResponse profile, String name) {
        return profile.certificates().stream().filter(item -> name.equals(item.name())).findFirst().orElseThrow();
    }

    private static StudentProfileResponse.Certificate findCertificateById(StudentProfileResponse profile, Long itemId) {
        return profile.certificates().stream().filter(item -> itemId.equals(item.itemId())).findFirst().orElseThrow();
    }

    private static StudentProfileResponse.Education findEducation(StudentProfileResponse profile, String schoolName) {
        return profile.education().stream().filter(item -> schoolName.equals(item.schoolName()))
                .findFirst().orElseThrow();
    }

    private static StudentProfileResponse.Education findEducationById(StudentProfileResponse profile, Long itemId) {
        return profile.education().stream().filter(item -> itemId.equals(item.itemId())).findFirst().orElseThrow();
    }

    private static StudentProfileResponse.Career findCareer(StudentProfileResponse profile, String companyName) {
        return profile.careers().stream().filter(item -> companyName.equals(item.companyName()))
                .findFirst().orElseThrow();
    }

    private static StudentProfileResponse.Career findCareerById(StudentProfileResponse profile, Long itemId) {
        return profile.careers().stream().filter(item -> itemId.equals(item.itemId())).findFirst().orElseThrow();
    }

    private ProfileItemRequest request(String name, String issuer, LocalDate acquiredOn) {
        return new ProfileItemRequest("CERTIFICATES",
                json.valueToTree(new ProfileItemRequest.CertificateItem(name, issuer, acquiredOn)));
    }

    private static String marker() {
        return "모의 DB 검증-" + UUID.randomUUID();
    }

    private record Baseline(StudentProfileResponse profile, List<CompetencyRow> competencies,
                            List<ProjectRow> projects, List<Map<String, Object>> students) {}

    private record ProjectRow(Long itemId, String title, String periodText, Integer teamSize,
                              String techStack, String roleNote, String readme, String portfolioFileName,
                              boolean includeInApply, String source) {}

    private record CompetencyRow(String profile, String sourceHash, LocalDateTime extractedAt) {}

    private record CoverLetterRow(String content, LocalDateTime updatedAt) {}
}
