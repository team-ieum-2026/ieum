// 주인: BE-B
package com.ieum.coordinator;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 취업·휴식 상태와 별도로 연락 대상을 읽는다. 상태 변경·도움 요청 생성·AI 호출은 하지 않는다. */
@Service
@Transactional(readOnly = true)
public class CoordinatorPassFollowUpService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final CoordinatorPassFollowUpMapper mapper;

    public CoordinatorPassFollowUpService(CoordinatorPassFollowUpMapper mapper) { this.mapper = mapper; }

    public List<CoordinatorPassFollowUpResponse> list() {
        return mapper.findFollowUps().stream().map(row -> new CoordinatorPassFollowUpResponse(
                row.getStudentId(), row.getStudentName(), row.getStatus(), row.getConsecutivePassCount(),
                row.getLastPassAt().atZone(SEOUL).toOffsetDateTime())).toList();
    }

    public Map<Long, Long> countsForStudents(List<Long> studentIds) {
        if (studentIds.isEmpty()) return Map.of();
        return mapper.findStreaks(studentIds).stream().collect(Collectors.toMap(
                CoordinatorPassFollowUpData::getStudentId, CoordinatorPassFollowUpData::getConsecutivePassCount));
    }
}
