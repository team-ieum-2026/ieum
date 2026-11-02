// 주인: BE-B
package com.ieum.coordinator;

import java.time.OffsetDateTime;

public record CoordinatorPassFollowUpResponse(Long studentId, String studentName, String status,
                                             long consecutivePassCount, OffsetDateTime lastPassAt) {}
