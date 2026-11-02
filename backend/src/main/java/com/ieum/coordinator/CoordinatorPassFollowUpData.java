// 주인: BE-B
package com.ieum.coordinator;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CoordinatorPassFollowUpData {
    private Long studentId;
    private String studentName;
    private String status;
    private long consecutivePassCount;
    private LocalDateTime lastPassAt;
}
