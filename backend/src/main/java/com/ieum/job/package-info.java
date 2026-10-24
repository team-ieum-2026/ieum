// 주인: BE-A
/**
 * 공고 수집 · 저장.
 * 화면: (배치) · 요구사항: BE-A-02 (서울 열린데이터광장 recMntList 일 배치 → job_posting upsert, POST /api/admin/jobs/sync),
 * BE-A-03 (배치 뒤 AI 색인 갱신). 필드 매핑 · IT 직종 필터 · 해시 ID 규칙은 docs/JOB_API.md.
 * 매퍼 XML: src/main/resources/mapper/job/
 */
package com.ieum.job;
