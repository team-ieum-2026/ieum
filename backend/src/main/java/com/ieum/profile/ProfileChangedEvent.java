// 주인: BE-B
package com.ieum.profile;

/** 등록 자료 변경이 커밋되면 최신 원문으로 역량 프로필을 다시 추출한다. */
public record ProfileChangedEvent(Long studentId) {}
