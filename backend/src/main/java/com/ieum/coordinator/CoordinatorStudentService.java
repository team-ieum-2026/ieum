// 주인: BE-B
package com.ieum.coordinator;

import com.ieum.common.BusinessException;
import com.ieum.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 목록·상세 조회는 같은 읽기 스냅샷을 사용하며, 상담 저장은 contact_log에만 기록한다. */
@Service
public class CoordinatorStudentService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> STATUSES = Set.of("EMPLOYED", "SEEKING", "NEED_HELP", "CHECK_REQUIRED");
    private static final Set<String> SUB_STATUSES = Set.of("APPLYING", "WAITING", "OTHER");
    private static final Set<String> CONTACT_TYPES = Set.of("PHONE", "MEETING", "MESSAGE");
    private static final int MAX_TEXT_BYTES = 65535;
    private final CoordinatorStudentMapper mapper;
    private final Clock clock;

    @Autowired
    public CoordinatorStudentService(CoordinatorStudentMapper mapper) {
        this(mapper, Clock.system(SEOUL));
    }

    CoordinatorStudentService(CoordinatorStudentMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock.withZone(SEOUL);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CoordinatorStudentResponse.ListResult list(String status, String subStatus, String q, int page, int size) {
        status = filterValue(status, STATUSES);
        subStatus = filterValue(subStatus, SUB_STATUSES);
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        String namePattern = namePattern(q);
        long offset = ((long) page - 1) * size;
        long total = mapper.countStudents(status, subStatus, namePattern);
        List<CoordinatorStudentResponse.Row> rows = total == 0 || offset >= total ? List.of()
                : mapper.findStudents(status, subStatus, namePattern, offset, size).stream()
                .map(CoordinatorStudentService::row).toList();
        return new CoordinatorStudentResponse.ListResult(rows, total, page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CoordinatorStudentResponse.Detail detail(Long studentId) {
        var student = requireStudent(studentId);
        var summary = mapper.findRegisteredSummary(studentId);
        var registered = new CoordinatorStudentResponse.Registered(summary.getEducation(), summary.getCertificates(),
                summary.getProjects(), summary.isHasCoverLetter());
        // 한 번에 학생의 이력을 읽어 묶는다. 추천 0건도 헤더와 items: []를 유지한다.
        var recommendations = mapper.findRecommendations(studentId);
        Map<Long, List<CoordinatorStudentResponse.HistoryItem>> items = new HashMap<>();
        for (var item : mapper.findHistoryItems(studentId)) {
            items.computeIfAbsent(item.getRecommendationId(), key -> new ArrayList<>()).add(
                    new CoordinatorStudentResponse.HistoryItem(item.getItemId(), item.getCompanyName(), item.getTitle(),
                            item.getResponse(), item.getPassReason(), item.getPassNote(), seoulTime(item.getRespondedAt()),
                            item.getApplicationId(), item.getApplicationStatus()));
        }
        var history = recommendations.stream().map(recommendation -> new CoordinatorStudentResponse.History(
                recommendation.getRecommendationId(), seoulTime(recommendation.getCreatedAt()),
                List.copyOf(items.getOrDefault(recommendation.getRecommendationId(), List.of())))).toList();
        var logs = mapper.findContactLogs(studentId).stream().map(CoordinatorStudentService::contactLog).toList();
        var basic = new CoordinatorStudentResponse.Student(student.getStudentId(), student.getName(),
                student.getCourseLabel(), student.getCompletedOn(), student.getStatus(), student.getSubStatus(),
                seoulTime(student.getLastResponseAt()), student.isHasNote(), student.getEmail(), student.getDesiredJob(),
                student.getPreferredRegion(), student.getReason());
        return new CoordinatorStudentResponse.Detail(basic, student.getCompetencyProfile(), registered, history, logs);
    }

    @Transactional
    public CoordinatorStudentResponse.ContactLog addContactLog(Long studentId, ContactLogCreateRequest request) {
        if (request == null || request.contactType() == null || !CONTACT_TYPES.contains(request.contactType())
                || request.note() == null || request.note().isBlank()
                || request.note().getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        requireStudent(studentId);
        var row = new CoordinatorStudentData.ContactLog();
        row.setStudentId(studentId);
        row.setContactType(request.contactType());
        row.setNote(request.note());
        row.setCreatedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        if (mapper.insertContactLog(row) != 1 || row.getContactLogId() == null || row.getContactLogId() <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        // 코디네이터 기록은 수료생 응답이 아니다. 상태 변경·AI 재추출 이벤트를 발행하지 않는다.
        return contactLog(row);
    }

    private CoordinatorStudentData.Student requireStudent(Long studentId) {
        if (studentId == null || studentId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        var student = mapper.findStudent(studentId);
        if (student == null) throw new BusinessException(ErrorCode.STUDENT_NOT_FOUND);
        return student;
    }

    private static String filterValue(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        if (!allowed.contains(value)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return value;
    }

    private static String namePattern(String q) {
        if (q == null || q.isBlank()) return null;
        return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static CoordinatorStudentResponse.Row row(CoordinatorStudentData.Student value) {
        return new CoordinatorStudentResponse.Row(value.getStudentId(), value.getName(), value.getCourseLabel(),
                value.getCompletedOn(), value.getStatus(), value.getSubStatus(), seoulTime(value.getLastResponseAt()),
                value.isHasNote());
    }

    private static CoordinatorStudentResponse.ContactLog contactLog(CoordinatorStudentData.ContactLog value) {
        return new CoordinatorStudentResponse.ContactLog(value.getContactLogId(), value.getContactType(),
                value.getNote(), seoulTime(value.getCreatedAt()));
    }

    private static OffsetDateTime seoulTime(LocalDateTime value) {
        return value == null ? null : value.atZone(SEOUL).toOffsetDateTime();
    }
}
