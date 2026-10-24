// 주인: BE-A
/**
 * 지원서 · 서류.
 * 화면: M5 · M6 · M6-1 · M6-2 · M7 · 요구사항: BE-A-07 (application 생성 → AI 서류 생성 비동기 → DRAFT/FAILED, 진행 상태),
 * BE-A-08 (서류 조회 · 수정 · 제출, 수정 항목 source=USER, saveToProfile 이면 BE-B 프로필 서비스 호출), BE-A-09 (지원 현황),
 * BE-A-10 (시연 기업 정보 수집 결과 저장).
 * application 테이블은 BE-A가 생성 · 제출, BE-B(coordinator)가 승인 · 반려 · 전달만 변경.
 * 매퍼 XML: src/main/resources/mapper/application/
 */
package com.ieum.application;
