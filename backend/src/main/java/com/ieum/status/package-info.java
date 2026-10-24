// 주인: BE-B
/**
 * 수료생 상태 판정.
 * 흐름: S12 · 요구사항: BE-B-06 (응답 · 도움 요청 · 제출 · 승인이 생길 때 재계산 + 하루 1회 1주 무응답 판정).
 * 규칙은 db/schema.sql 맨 아래. 상태 값: EMPLOYED · SEEKING(APPLYING · WAITING · OTHER) · NEED_HELP · CHECK_REQUIRED.
 * 매퍼 XML: src/main/resources/mapper/status/
 */
package com.ieum.status;
