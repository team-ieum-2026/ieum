// 주인: BE-B
package com.ieum.helprequest;

import java.time.OffsetDateTime;

/** 수료생이 조회하는 도움 요청. 추천 공고가 없는 요청의 관련 필드는 null이다. */
public record HelpRequestListItem(Long helpRequestId, Long studentId, Long recommendationItemId,
                                  String helpType, String status, String contactMethod, String memo,
                                  OffsetDateTime createdAt, OffsetDateTime closedAt,
                                  String closeReason, String jobTitle) {}
