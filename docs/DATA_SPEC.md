# 이음 P0 데이터 명세서 (테이블 정의서)

`db/schema.sql` 기준입니다. 스키마가 바뀌면 BE-B가 이 문서도 함께 고칩니다. 관계도는 `docs/ERD.md`, JSON 컬럼 안의 형식은 `docs/API.md` 3장을 봅니다.

- DB: MySQL 8.0 · 데이터베이스 `ieum` · utf8mb4 · 테이블 17개
- 이름 규칙: 테이블 · 컬럼은 snake_case, API로 나갈 때 camelCase (`deadline_on` → `deadlineOn`)
- 상태 · 종류 값은 UPPER_SNAKE 문자열(VARCHAR)로 저장 · 날짜(`DATE`)는 `~_on`, 시각(`DATETIME`)은 `~_at`
- 키: PK 기본키 · FK 외래키(→ 참조 대상) · UK 유일 · NULL 칸의 Y는 비어 있어도 됨

## 목차

1. [`course`](#course) — 교육 과정
2. [`student`](#student) — 수료생
3. [`student_education`](#student_education) — 학력
4. [`student_career`](#student_career) — 경력
5. [`student_certificate`](#student_certificate) — 자격증
6. [`student_project`](#student_project) — 프로젝트 · 포트폴리오
7. [`student_cover_letter`](#student_cover_letter) — 자기소개서 원본
8. [`competency_profile`](#competency_profile) — 역량 프로필
9. [`job_posting`](#job_posting) — 채용 공고
10. [`company_info`](#company_info) — 기업 정보
11. [`recommendation`](#recommendation) — 추천 묶음
12. [`recommendation_item`](#recommendation_item) — 추천 공고와 응답
13. [`help_request`](#help_request) — 도움 요청
14. [`application`](#application) — 지원서
15. [`application_document`](#application_document) — 지원서 본문
16. [`student_status`](#student_status) — 수료생 상태
17. [`contact_log`](#contact_log) — 통화 · 상담 기록

## course

교육 과정 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `name` | VARCHAR(100) | N |  |  | 예: AI Agent 실무 프로젝트 기반 풀스택 개발자 양성과정 |
| `cohort` | VARCHAR(20) | N |  |  | 기수. 예: 4기 |
| `campus` | VARCHAR(50) | N |  |  | 예: 동대문캠퍼스 |
| `started_on` | DATE | N |  |  |  |
| `ended_on` | DATE | N |  |  |  |
| `curriculum_skills` | JSON | Y |  |  | 커리큘럼 기술 목록 ["Java","Spring Boot",...] |

## student

수료생 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `course_id` | BIGINT | N |  | FK → `course.id` |  |
| `name` | VARCHAR(50) | N |  |  | 시드는 모의 이름(가명) |
| `email` | VARCHAR(100) | Y |  |  |  |
| `github_url` | VARCHAR(200) | Y |  |  |  |
| `desired_job` | VARCHAR(100) | Y |  |  | 희망 직무 |
| `preferred_region` | VARCHAR(100) | Y |  |  | 선호 지역 |
| `military_status` | VARCHAR(20) | Y |  |  | SERVED / NOT_SERVED / EXEMPT / NOT_APPLICABLE / NULL(입력 안 함) |
| `skills` | JSON | Y |  |  | 직접 입력 · 확인한 기술 목록 ["Java","React"]. 역량 프로필(AI 추출)과 별개 |
| `completed_on` | DATE | N |  |  | 수료일 |
| `last_response_at` | DATETIME | Y |  |  | 마지막 응답(지원·패스·도움 요청·서류 제출) 시각. 1주 무응답 판정에 사용 |
| `created_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |
| `updated_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## student_education

학력 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `school_name` | VARCHAR(100) | N |  |  |  |
| `major` | VARCHAR(100) | Y |  |  |  |
| `degree` | VARCHAR(30) | Y |  |  | HIGH_SCHOOL / ASSOCIATE / BACHELOR / MASTER / DOCTOR |
| `status` | VARCHAR(20) | Y |  |  | GRADUATED / EXPECTED / ENROLLED / DROPPED |
| `graduated_on` | DATE | Y |  |  |  |
| `source` | VARCHAR(20) | N | REGISTERED |  | REGISTERED(기관 등록) / USER(수료생 직접 입력) |

## student_career

경력 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `company_name` | VARCHAR(100) | N |  |  |  |
| `position` | VARCHAR(100) | Y |  |  |  |
| `started_on` | DATE | Y |  |  |  |
| `ended_on` | DATE | Y |  |  | NULL이면 재직 중 |
| `description` | TEXT | Y |  |  |  |
| `source` | VARCHAR(20) | N | REGISTERED |  | REGISTERED / USER |

## student_certificate

자격증 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `name` | VARCHAR(100) | N |  |  |  |
| `issuer` | VARCHAR(100) | Y |  |  |  |
| `acquired_on` | DATE | Y |  |  |  |
| `source` | VARCHAR(20) | N | REGISTERED |  | REGISTERED / USER |

## student_project

프로젝트 · 포트폴리오 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `title` | VARCHAR(150) | N |  |  |  |
| `period_text` | VARCHAR(50) | Y |  |  | 예: 2026.07 – 2026.08 (6주) |
| `team_size` | INT | Y |  |  |  |
| `role_note` | TEXT | Y |  |  | 본인 역할 기록 |
| `readme` | MEDIUMTEXT | Y |  |  | README 원문 (텍스트) |
| `tech_stack` | JSON | Y |  |  | ["Spring Boot","MySQL"] |
| `portfolio_file_url` | VARCHAR(300) | Y |  |  | 포트폴리오 파일 경로 (P0는 파일명만) |
| `include_in_apply` | TINYINT(1) | N | 1 |  | 기관 추천 시 포트폴리오 함께 제출 여부 |
| `source` | VARCHAR(20) | N | REGISTERED |  |  |

## student_cover_letter

자기소개서 원본 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `student_id` | BIGINT | N |  | PK · FK → `student.id` |  |
| `content` | MEDIUMTEXT | N |  |  |  |
| `updated_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## competency_profile

역량 프로필 · 쓰는 쪽: BE-B (AI-B 추출 결과 저장)

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `student_id` | BIGINT | N |  | PK · FK → `student.id` |  |
| `profile` | JSON | N |  |  | 역량 프로필 JSON (docs/API.md 참고) |
| `source_hash` | CHAR(64) | Y |  |  | 추출에 쓴 원문 해시. 원문이 바뀌면 재추출 |
| `extracted_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |

## job_posting

채용 공고 · 쓰는 쪽: BE-A

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `source` | VARCHAR(20) | N | SEOUL_API | UK | SEOUL_API / PARTNER(기관 연계, 운영 단계) |
| `external_id` | CHAR(40) | N |  | UK | API에 공고 ID가 없어 SHA-1(COMPANY/TITLE/REG_DT/STRTNM_CD)로 생성 |
| `company_name` | VARCHAR(150) | N |  |  | COMPANY |
| `title` | VARCHAR(300) | N |  |  | TITLE |
| `job_code` | VARCHAR(10) | Y |  |  | JOBS_CD 모집 직종 코드. IT 직종 필터에 사용 |
| `job_category` | VARCHAR(100) | Y |  |  | JOBS_NM에서 괄호 코드를 뺀 이름 |
| `location` | VARCHAR(100) | Y |  |  | REGION. 예: 서울 노원구 |
| `work_address` | VARCHAR(300) | Y |  |  | WORK_REGION 근무지 주소 |
| `employment_type` | VARCHAR(50) | Y |  |  | EMP_TP_CD를 짧은 이름으로 (정규직 · 계약직 등) |
| `career` | VARCHAR(50) | Y |  |  | CAREER. 신입 · 경력 · 관계없음 |
| `education` | VARCHAR(50) | Y |  |  | MIN_EDUBG |
| `salary_text` | VARCHAR(100) | Y |  |  | SAL_TP_NM 끝의 쉼표 제거 |
| `description` | MEDIUMTEXT | Y |  |  | 매칭용 본문: JOB_CONT + 우대 · 자격 항목을 합친 글 |
| `homepage_url` | VARCHAR(300) | Y |  |  | API에 없음. 운영 단계 기관 연계 공고용 |
| `deadline_on` | DATE | Y |  |  | CLOSE_DT의 날짜 부분 (YY-MM-DD → YYYY-MM-DD) |
| `until_filled` | TINYINT(1) | N | 0 |  | CLOSE_DT가 "채용시까지"로 시작하면 1 (그 날짜 전에도 마감될 수 있음) |
| `posted_on` | DATE | Y |  |  | REG_DT |
| `is_active` | TINYINT(1) | N | 1 |  | 마감일이 지났거나 API에서 사라지면 0 |
| `content_hash` | CHAR(64) | Y |  |  | 본문 해시. 바뀐 경우에만 임베딩 재계산 |
| `raw` | JSON | Y |  |  | API 원본 한 건 |
| `synced_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## company_info

기업 정보 · 쓰는 쪽: BE-A (AI-B 수집 결과 저장)

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `company_name` | VARCHAR(150) | N |  | UK |  |
| `status` | VARCHAR(20) | N |  |  | FOUND / PARTIAL / NOT_FOUND (NOT_FOUND면 "공고 외 정보 확인 불가") |
| `info` | JSON | Y |  |  | {talentKeywords, coreValues, recentIssue, sources[{item,url,collectedAt}]} |
| `collected_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |

## recommendation

추천 묶음 · 쓰는 쪽: BE-A

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `trigger_type` | VARCHAR(20) | N |  |  | VISIT(접속) / REFRESH(새로 추천받기) / BATCH(운영: 매주 월요일) |
| `created_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |

## recommendation_item

추천 공고와 응답 · 쓰는 쪽: BE-A

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `recommendation_id` | BIGINT | N |  | FK → `recommendation.id` · UK |  |
| `job_posting_id` | BIGINT | N |  | FK → `job_posting.id` |  |
| `rank_no` | TINYINT | N |  | UK | 1~3 |
| `reasons` | JSON | N |  |  | 추천 근거 [{text, evidenceRef}] (카드 2줄 + 상세) |
| `response` | VARCHAR(20) | N | NONE |  | NONE / APPLY / PASS / HELP |
| `pass_reason` | VARCHAR(30) | Y |  |  | ALREADY_EMPLOYED / JOB_MISMATCH / CONDITION_MISMATCH / SKILL_GAP / TAKING_BREAK / OTHER |
| `pass_note` | TEXT | Y |  |  | 기타 자유 입력 = 비고. 분류하지 않고 코디네이터가 확인 |
| `responded_at` | DATETIME | Y |  |  |  |

## help_request

도움 요청 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `recommendation_item_id` | BIGINT | Y |  | FK → `recommendation_item.id` |  |
| `contact_method` | VARCHAR(20) | Y |  |  | SMS / PHONE / EMAIL |
| `memo` | TEXT | Y |  |  |  |
| `status` | VARCHAR(20) | N | OPEN |  | OPEN / CONTACTED / CLOSED |
| `created_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |
| `closed_at` | DATETIME | Y |  |  |  |

## application

지원서 · 쓰는 쪽: BE-A 생성 · 제출 / BE-B 승인 · 반려 · 전달

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `recommendation_item_id` | BIGINT | N |  | FK → `recommendation_item.id` · UK |  |
| `job_posting_id` | BIGINT | N |  | FK → `job_posting.id` |  |
| `status` | VARCHAR(20) | N | GENERATING |  | GENERATING / DRAFT / SUBMITTED / APPROVED / REJECTED / DELIVERED / FAILED |
| `submitted_at` | DATETIME | Y |  |  |  |
| `reviewed_at` | DATETIME | Y |  |  |  |
| `delivered_at` | DATETIME | Y |  |  |  |
| `reject_note` | TEXT | Y |  |  |  |
| `created_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |
| `updated_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## application_document

지원서 본문 · 쓰는 쪽: BE-A (AI-A 생성 결과 저장)

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `application_id` | BIGINT | N |  | PK · FK → `application.id` |  |
| `document` | JSON | N |  |  | 서류 JSON (docs/API.md 참고) |
| `updated_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## student_status

수료생 상태 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `student_id` | BIGINT | N |  | PK · FK → `student.id` |  |
| `status` | VARCHAR(20) | N |  |  | EMPLOYED / SEEKING / NEED_HELP / CHECK_REQUIRED |
| `sub_status` | VARCHAR(20) | Y |  |  | SEEKING일 때: APPLYING(기관 추천 지원 중) / WAITING(패스 후 대기) / OTHER(기타 사유) |
| `reason` | VARCHAR(200) | Y |  |  | 판정 근거 한 줄. 예: 패스 사유 ALREADY_EMPLOYED |
| `updated_at` | DATETIME | N | CURRENT_TIMESTAMP (수정 시 갱신) |  |  |

## contact_log

통화 · 상담 기록 · 쓰는 쪽: BE-B

| 컬럼 | 타입 | NULL | 기본값 | 키 | 설명 |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT | N |  | PK · 자동 증가 |  |
| `student_id` | BIGINT | N |  | FK → `student.id` |  |
| `contact_type` | VARCHAR(20) | N |  |  | PHONE / MESSAGE / MEETING |
| `note` | TEXT | Y |  |  |  |
| `created_by` | VARCHAR(50) | N | coordinator |  | P0는 로그인 없음 |
| `created_at` | DATETIME | N | CURRENT_TIMESTAMP |  |  |

## 상태 판정 규칙 (student_status, BE-B)

위에서부터 먼저 맞는 것을 적용합니다.

| 순서 | 상태 | 조건 |
| --- | --- | --- |
| 1 | `EMPLOYED` 취업 완료 | 패스 사유가 `ALREADY_EMPLOYED`인 응답이 있음 |
| 2 | `NEED_HELP` 도움 필요 | `status = 'OPEN'`인 `help_request`가 있음 |
| 3 | `CHECK_REQUIRED` 추가 확인 필요 | `last_response_at`이 7일 이상 지남 |
| 4 | `SEEKING` 구직 중 | 그 외. 세부: `APPLYING`(제출 · 승인 · 전달된 지원서 있음) → `OTHER`(최근 패스 사유가 기타) → `WAITING`(나머지) |
