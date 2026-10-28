// 주인: BE-B
package com.ieum.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ieum.ai.RestAiClient;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** 실제 RestAiClient의 HTTP 요청과 빈 LLM 결과 저장을 키·LLM 호출 없이 검증한다. */
class ProfileExtractionContractTest {

    @Test
    void sendsRegisteredIdsAndOriginalSourcesOverHttpAndAcceptsEmptyLlmArrays() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ProfileMapper mapper = mock(ProfileMapper.class);
        ProfileData.Student student = new ProfileData.Student();
        student.setStudentId(96L);
        student.setName("모의 수료생");
        student.setCompletedOn(LocalDate.of(2026, 10, 1));
        student.setCourseId(85L);
        student.setCourseName("모의 개발 과정");
        student.setCohort("모의 기수");
        student.setCampus("모의 캠퍼스");
        student.setStartedOn(LocalDate.of(2026, 4, 1));
        student.setEndedOn(LocalDate.of(2026, 10, 1));
        student.setCoverLetterContent("모의 프로젝트에서 API 구현을 담당했습니다.");
        student.setCoverLetterUpdatedAt(LocalDateTime.of(2026, 10, 28, 10, 0));
        ProfileData.Project project = new ProfileData.Project();
        project.setItemId(74L);
        project.setTitle("모의 프로젝트");
        project.setRoleNote("모의 API 개발");
        project.setReadme("모의 처리 시간을 30% 줄였습니다.");
        project.setSource("REGISTERED");
        project.setTechStack(json.createArrayNode().add("Java"));
        project.setIncludeInApply(true);
        when(mapper.findStudent(96L)).thenReturn(student);
        when(mapper.findProjects(96L)).thenReturn(List.of(project));
        when(mapper.findEducation(96L)).thenReturn(List.of(new StudentProfileResponse.Education(
                41L, "USER", "모의 학교", null, null, null, null)));
        when(mapper.findCareers(96L)).thenReturn(List.of(new StudentProfileResponse.Career(
                52L, "USER", "모의 기업", null, null, null, "경력 근거는 추출하지 않음")));
        when(mapper.findCertificates(96L)).thenReturn(List.of(new StudentProfileResponse.Certificate(
                63L, "USER", "모의 자격증", null, null)));
        StudentProfileResponse registered = new ProfileService(mapper, mock(ApplicationEventPublisher.class))
                .getProfile(96L);

        AtomicReference<JsonNode> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/profile/extract", exchange -> {
            received.set(json.readTree(exchange.getRequestBody()));
            byte[] response = "{\"data\":{\"profile\":{\"roles\":[],\"skills\":[],\"achievements\":[],\"certificates\":[],\"desiredJob\":null,\"preferredRegion\":null}}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.start();
        try {
            var extraction = new ProfileExtraction(mapper,
                    new RestAiClient("http://127.0.0.1:" + server.getAddress().getPort()), json);
            extraction.refresh(96L, registered);
            JsonNode request = received.get();
            assertNotNull(request);
            assertEquals(96L, request.path("studentId").longValue());
            assertEquals(85L, request.path("course").path("courseId").longValue());
            assertEquals(41L, request.path("education").get(0).path("itemId").longValue());
            assertEquals(52L, request.path("careers").get(0).path("itemId").longValue());
            assertEquals(63L, request.path("certificates").get(0).path("itemId").longValue());
            assertEquals(74L, request.path("projects").get(0).path("itemId").longValue());
            assertEquals(project.getRoleNote(), request.path("projects").get(0).path("roleNote").textValue());
            assertEquals(project.getReadme(), request.path("projects").get(0).path("readme").textValue());
            assertEquals("REGISTERED", request.path("projects").get(0).path("source").textValue());
            assertTrue(request.path("basic").get("email").isNull());
            assertTrue(request.path("projects").get(0).get("periodText").isNull());
            assertEquals("2026-10-01", request.path("basic").path("completedOn").textValue());
            assertEquals("2026-10-28T10:00:00+09:00", request.path("coverLetter").path("updatedAt").textValue());
            assertFalse(request.has("competencyProfile"));
            assertFalse(request.has("profileUpdatedAt"));
            verify(mapper).upsertCompetencyProfile(eq(96L), argThat(profile ->
                    profile.path("roles").isEmpty() && profile.path("achievements").isEmpty()),
                    matches("[0-9a-f]{64}"));
            // AI의 Pydantic/FastAPI 계약 검사에서도 같은 Java 직렬화 결과를 사용한다. 모두 모의 자료다.
            Path fixture = Path.of("build", "profile-extract-contract", "request.json");
            Files.createDirectories(fixture.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(fixture.toFile(), request);
        } finally {
            server.stop(0);
        }
    }
}
