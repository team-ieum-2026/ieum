// 주인: BE-B
package com.ieum.status;

import java.time.OffsetDateTime;

public record StudentStatusResult(Long studentId, String status, String subStatus, OffsetDateTime updatedAt) {}
