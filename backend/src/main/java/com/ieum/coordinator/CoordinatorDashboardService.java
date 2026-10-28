// 주인: BE-B
package com.ieum.coordinator;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 같은 읽기 스냅샷의 학생 집계와 할 일을 반환한다. AI 호출이나 상태 저장은 하지 않는다. */
@Service
public class CoordinatorDashboardService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CoordinatorDashboardMapper mapper;
    private final Clock clock;

    @Autowired
    public CoordinatorDashboardService(CoordinatorDashboardMapper mapper) {
        this(mapper, Clock.system(SEOUL));
    }

    CoordinatorDashboardService(CoordinatorDashboardMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock.withZone(SEOUL);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CoordinatorDashboardResponse getDashboard() {
        OffsetDateTime asOf = OffsetDateTime.now(clock);
        List<CoordinatorDashboardData.StudentState> students = mapper.findStudentStates();
        long employed = 0, needHelp = 0, checkRequired = 0, applying = 0, waiting = 0, other = 0;
        List<CoordinatorDashboardData.StudentState> calls = new ArrayList<>();
        for (var student : students) {
            switch (displayStatus(student.getStatus())) {
                case "EMPLOYED" -> employed++;
                case "NEED_HELP" -> needHelp++;
                case "CHECK_REQUIRED" -> {
                    checkRequired++;
                    calls.add(student);
                }
                case "SEEKING" -> {
                    if ("APPLYING".equals(student.getSubStatus())) applying++;
                    else if ("OTHER".equals(student.getSubStatus())) other++;
                    else waiting++;
                }
                default -> throw new IllegalStateException("알 수 없는 표시 상태예요.");
            }
        }
        var summary = new CoordinatorDashboardResponse.StatusSummary(employed,
                new CoordinatorDashboardResponse.Seeking(applying + waiting + other, applying, waiting, other),
                needHelp, checkRequired);
        var callList = calls.stream()
                .sorted(Comparator.comparing(CoordinatorDashboardService::inactivitySince)
                        .thenComparing(CoordinatorDashboardData.StudentState::getStudentId))
                .map(student -> new CoordinatorDashboardResponse.CallListItem(student.getStudentId(),
                        student.getStudentName(), seoulTime(student.getLastResponseAt()),
                        Math.max(0, Duration.between(inactivitySince(student), asOf.toLocalDateTime()).toDays())))
                .toList();
        var help = mapper.findOpenHelpRequests().stream()
                .map(row -> new CoordinatorDashboardResponse.HelpRequest(row.getHelpRequestId(), row.getStudentId(),
                        row.getStudentName(), row.getCourseLabel(), seoulTime(row.getCreatedAt()), row.getJobTitle(),
                        row.getContactMethod(), row.getMemo())).toList();
        var review = mapper.findReviewQueue().stream()
                .map(row -> new CoordinatorDashboardResponse.ReviewQueueItem(row.getApplicationId(),
                        row.getStudentName(), row.getJobTitle(), seoulTime(row.getSubmittedAt()))).toList();
        var notes = mapper.findLatestPassNotes().stream()
                .map(row -> new CoordinatorDashboardResponse.NoteToCheck(row.getStudentId(), row.getStudentName(),
                        row.getPassNote())).toList();
        return new CoordinatorDashboardResponse(asOf, students.size(), help, summary, review, callList, notes);
    }

    private static String displayStatus(String status) {
        // 아직 판정 행이 없는 학생도 인원에서 누락하지 않는다. 조회 결과에만 확인 필요로 표시한다.
        if ("EMPLOYED".equals(status) || "SEEKING".equals(status) || "NEED_HELP".equals(status)) return status;
        return "CHECK_REQUIRED";
    }

    private static LocalDateTime inactivitySince(CoordinatorDashboardData.StudentState student) {
        return student.getLastResponseAt() == null
                ? student.getCompletedOn().atStartOfDay() : student.getLastResponseAt();
    }

    private static OffsetDateTime seoulTime(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
