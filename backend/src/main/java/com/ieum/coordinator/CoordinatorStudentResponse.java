// 주인: BE-B
package com.ieum.coordinator;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** C2·C3 응답. frontend/src/types/student.ts의 기존 목록·상세·상담 기록 계약을 따른다. */
public final class CoordinatorStudentResponse {
    private CoordinatorStudentResponse() {}

    public record ListResult(List<Row> items, long total, int page, int size) {}

    public record Row(Long studentId, String name, String courseLabel, LocalDate completedOn,
                      String status, String subStatus, OffsetDateTime lastResponseAt, boolean hasNote) {}

    public record Detail(Student student, JsonNode competencyProfile, Registered registered,
                         List<History> history, List<ContactLog> contactLogs) {}

    public record Student(Long studentId, String name, String courseLabel, LocalDate completedOn,
                          String status, String subStatus, OffsetDateTime lastResponseAt, boolean hasNote,
                          String email, String desiredJob, String preferredRegion, String reason) {}

    public record Registered(int education, int certificates, int projects, boolean hasCoverLetter) {}

    public record History(Long recommendationId, OffsetDateTime createdAt, List<HistoryItem> items) {}

    public record HistoryItem(Long itemId, String companyName, String title, String response,
                              String passReason, String passNote, OffsetDateTime respondedAt,
                              Long applicationId, String applicationStatus) {}

    public record ContactLog(Long contactLogId, String contactType, String note, OffsetDateTime createdAt) {}
}
