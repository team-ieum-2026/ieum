// 주인: BE-B
package com.ieum.helprequest;

import java.time.OffsetDateTime;

public record HelpRequestCloseResult(Long helpRequestId, String status,
                                     OffsetDateTime closedAt, String closeReason) {}
