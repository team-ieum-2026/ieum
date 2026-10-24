-- =====================================================================
-- 이음 P0 DB 스키마 (MySQL 8.0)
-- 주인: BE-B (유능한) · 초안: PM · 월요일 킥오프에서 BE-B 확정
-- 규칙: 테이블 · 컬럼은 snake_case, 상태값은 UPPER_SNAKE 문자열(VARCHAR) 저장
--       JSON 컬럼의 내부 형식은 docs/API.md의 "핵심 JSON 형식"을 따른다 (주인: AI-A)
-- 실행: mysql -u root -p < db/schema.sql  →  mysql -u root -p ieum < db/seed.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ieum DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ieum;

-- ---------------------------------------------------------------------
-- 1. 과정 · 수료생 (S1 등록 자료)
-- ---------------------------------------------------------------------

-- 교육 과정. 수료생별이 아니라 과정별 1건
CREATE TABLE course (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  name               VARCHAR(100) NOT NULL COMMENT '예: AI Agent 실무 프로젝트 기반 풀스택 개발자 양성과정',
  cohort             VARCHAR(20)  NOT NULL COMMENT '기수. 예: 4기',
  campus             VARCHAR(50)  NOT NULL COMMENT '예: 동대문캠퍼스',
  started_on         DATE         NOT NULL,
  ended_on           DATE         NOT NULL,
  curriculum_skills  JSON         NULL     COMMENT '커리큘럼 기술 목록 ["Java","Spring Boot",...]',
  PRIMARY KEY (id)
) COMMENT '교육 과정';

-- 수료생 (기본정보 + 상태 판정용 마지막 응답 시각)
CREATE TABLE student (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  course_id          BIGINT       NOT NULL,
  name               VARCHAR(50)  NOT NULL COMMENT '시드는 모의 이름(가명)',
  email              VARCHAR(100) NULL,
  github_url         VARCHAR(200) NULL,
  desired_job        VARCHAR(100) NULL     COMMENT '희망 직무',
  preferred_region   VARCHAR(100) NULL     COMMENT '선호 지역',
  military_status    VARCHAR(20)  NULL     COMMENT 'SERVED | NOT_SERVED | EXEMPT | NOT_APPLICABLE | NULL(입력 안 함)',
  skills             JSON         NULL     COMMENT '직접 입력 · 확인한 기술 목록 ["Java","React"]. 역량 프로필(AI 추출)과 별개',
  completed_on       DATE         NOT NULL COMMENT '수료일',
  last_response_at   DATETIME     NULL     COMMENT '마지막 응답(지원·패스·도움 요청·서류 제출) 시각. 1주 무응답 판정에 사용',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_student_course FOREIGN KEY (course_id) REFERENCES course (id)
) COMMENT '수료생';

-- 학력 (M6 · M8 학력 섹션)
CREATE TABLE student_education (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  school_name        VARCHAR(100) NOT NULL,
  major              VARCHAR(100) NULL,
  degree             VARCHAR(30)  NULL     COMMENT 'HIGH_SCHOOL | ASSOCIATE | BACHELOR | MASTER | DOCTOR',
  status             VARCHAR(20)  NULL     COMMENT 'GRADUATED | EXPECTED | ENROLLED | DROPPED',
  graduated_on       DATE         NULL,
  source             VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED(기관 등록) | USER(수료생 직접 입력)',
  PRIMARY KEY (id),
  CONSTRAINT fk_edu_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '학력';

-- 경력 (M6 · M8 경력 섹션. 신입이 대부분이라 비어 있어도 됨)
CREATE TABLE student_career (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  company_name       VARCHAR(100) NOT NULL,
  position           VARCHAR(100) NULL,
  started_on         DATE         NULL,
  ended_on           DATE         NULL     COMMENT 'NULL이면 재직 중',
  description        TEXT         NULL,
  source             VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED | USER',
  PRIMARY KEY (id),
  CONSTRAINT fk_career_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '경력';

-- 자격증 (M6-2 · M8 자격증 섹션)
CREATE TABLE student_certificate (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  name               VARCHAR(100) NOT NULL,
  issuer             VARCHAR(100) NULL,
  acquired_on        DATE         NULL,
  source             VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED | USER',
  PRIMARY KEY (id),
  CONSTRAINT fk_cert_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '자격증';

-- 프로젝트 · 포트폴리오 원문 (S2 추출의 입력, 근거 원문)
CREATE TABLE student_project (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  title              VARCHAR(150) NOT NULL,
  period_text        VARCHAR(50)  NULL     COMMENT '예: 2026.07 – 2026.08 (6주)',
  team_size          INT          NULL,
  role_note          TEXT         NULL     COMMENT '본인 역할 기록',
  readme             MEDIUMTEXT   NULL     COMMENT 'README 원문 (텍스트)',
  tech_stack         JSON         NULL     COMMENT '["Spring Boot","MySQL"]',
  portfolio_file_url VARCHAR(300) NULL     COMMENT '포트폴리오 파일 경로 (P0는 파일명만)',
  include_in_apply   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '기관 추천 시 포트폴리오 함께 제출 여부',
  source             VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
  PRIMARY KEY (id),
  CONSTRAINT fk_project_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '프로젝트 · 포트폴리오';

-- 자기소개서 원본 (공고별 초안의 바탕, M8에서 수정)
CREATE TABLE student_cover_letter (
  student_id         BIGINT       NOT NULL,
  content            MEDIUMTEXT   NOT NULL,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (student_id),
  CONSTRAINT fk_cl_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '자기소개서 원본';

-- 역량 프로필 (S2 결과). 형식 주인 AI-A, 생성 AI-B
CREATE TABLE competency_profile (
  student_id         BIGINT       NOT NULL,
  profile            JSON         NOT NULL COMMENT '역량 프로필 JSON (docs/API.md 참고)',
  source_hash        CHAR(64)     NULL     COMMENT '추출에 쓴 원문 해시. 원문이 바뀌면 재추출',
  extracted_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (student_id),
  CONSTRAINT fk_profile_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '역량 프로필';

-- ---------------------------------------------------------------------
-- 2. 공고 · 기업 정보 (S3, S7)
-- ---------------------------------------------------------------------

-- 채용 공고. 서울 열린데이터광장 채용정보 API 일 배치로 upsert (필드 매핑: docs/JOB_API.md)
CREATE TABLE job_posting (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  source             VARCHAR(20)  NOT NULL DEFAULT 'SEOUL_API' COMMENT 'SEOUL_API | PARTNER(기관 연계, 운영 단계)',
  external_id        CHAR(40)     NOT NULL COMMENT 'API에 공고 ID가 없어 SHA-1(COMPANY|TITLE|REG_DT|STRTNM_CD)로 생성',
  company_name       VARCHAR(150) NOT NULL COMMENT 'COMPANY',
  title              VARCHAR(300) NOT NULL COMMENT 'TITLE',
  job_code           VARCHAR(10)  NULL     COMMENT 'JOBS_CD 모집 직종 코드. IT 직종 필터에 사용',
  job_category       VARCHAR(100) NULL     COMMENT 'JOBS_NM에서 괄호 코드를 뺀 이름',
  location           VARCHAR(100) NULL     COMMENT 'REGION. 예: 서울 노원구',
  work_address       VARCHAR(300) NULL     COMMENT 'WORK_REGION 근무지 주소',
  employment_type    VARCHAR(50)  NULL     COMMENT 'EMP_TP_CD를 짧은 이름으로 (정규직 · 계약직 등)',
  career             VARCHAR(50)  NULL     COMMENT 'CAREER. 신입 · 경력 · 관계없음',
  education          VARCHAR(50)  NULL     COMMENT 'MIN_EDUBG',
  salary_text        VARCHAR(100) NULL     COMMENT 'SAL_TP_NM 끝의 쉼표 제거',
  description        MEDIUMTEXT   NULL     COMMENT '매칭용 본문: JOB_CONT + 우대 · 자격 항목을 합친 글',
  homepage_url       VARCHAR(300) NULL     COMMENT 'API에 없음. 운영 단계 기관 연계 공고용',
  deadline_on        DATE         NULL     COMMENT 'CLOSE_DT의 날짜 부분 (YY-MM-DD → YYYY-MM-DD)',
  until_filled       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'CLOSE_DT가 "채용시까지"로 시작하면 1 (그 날짜 전에도 마감될 수 있음)',
  posted_on          DATE         NULL     COMMENT 'REG_DT',
  is_active          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '마감일이 지났거나 API에서 사라지면 0',
  content_hash       CHAR(64)     NULL     COMMENT '본문 해시. 바뀐 경우에만 임베딩 재계산',
  raw                JSON         NULL     COMMENT 'API 원본 한 건',
  synced_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_source_ext (source, external_id),
  KEY idx_job_active_deadline (is_active, deadline_on),
  KEY idx_job_code (job_code)
) COMMENT '채용 공고';

-- 기업 정보 (웹 검색 RAG 수집 결과). 형식 주인 AI-A, 수집 AI-B
CREATE TABLE company_info (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  company_name       VARCHAR(150) NOT NULL,
  status             VARCHAR(20)  NOT NULL COMMENT 'FOUND | PARTIAL | NOT_FOUND (NOT_FOUND면 "공고 외 정보 확인 불가")',
  info               JSON         NULL     COMMENT '{talentKeywords, coreValues, recentIssue, sources[{item,url,collectedAt}]}',
  collected_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_company_name (company_name)
) COMMENT '기업 정보';

-- ---------------------------------------------------------------------
-- 3. 추천 · 응답 (S3 ~ S6, S11)
-- ---------------------------------------------------------------------

-- 추천 묶음 (한 번에 Top-3)
CREATE TABLE recommendation (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  trigger_type       VARCHAR(20)  NOT NULL COMMENT 'VISIT(접속) | REFRESH(새로 추천받기) | BATCH(운영: 매주 월요일)',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rec_student (student_id, created_at),
  CONSTRAINT fk_rec_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '추천 묶음';

-- 추천 공고 1건 + 수료생 응답
CREATE TABLE recommendation_item (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  recommendation_id  BIGINT       NOT NULL,
  job_posting_id     BIGINT       NOT NULL,
  rank_no            TINYINT      NOT NULL COMMENT '1~3',
  reasons            JSON         NOT NULL COMMENT '추천 근거 [{text, evidenceRef}] (카드 2줄 + 상세)',
  response           VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE | APPLY | PASS | HELP',
  pass_reason        VARCHAR(30)  NULL     COMMENT 'ALREADY_EMPLOYED | JOB_MISMATCH | CONDITION_MISMATCH | SKILL_GAP | TAKING_BREAK | OTHER',
  pass_note          TEXT         NULL     COMMENT '기타 자유 입력 = 비고. 분류하지 않고 코디네이터가 확인',
  responded_at       DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_item_rank (recommendation_id, rank_no),
  CONSTRAINT fk_item_rec FOREIGN KEY (recommendation_id) REFERENCES recommendation (id) ON DELETE CASCADE,
  CONSTRAINT fk_item_job FOREIGN KEY (job_posting_id) REFERENCES job_posting (id)
) COMMENT '추천 공고와 응답';

-- 도움 요청 (M2 → M4 → C1)
CREATE TABLE help_request (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  student_id             BIGINT       NOT NULL,
  recommendation_item_id BIGINT       NULL,
  contact_method         VARCHAR(20)  NULL     COMMENT 'SMS | PHONE | EMAIL',
  memo                   TEXT         NULL,
  status                 VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN | CONTACTED | CLOSED',
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at              DATETIME     NULL,
  PRIMARY KEY (id),
  KEY idx_help_status (status, created_at),
  CONSTRAINT fk_help_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
  CONSTRAINT fk_help_item FOREIGN KEY (recommendation_item_id) REFERENCES recommendation_item (id)
) COMMENT '도움 요청';

-- ---------------------------------------------------------------------
-- 4. 지원 서류 (S7 ~ S10)
-- ---------------------------------------------------------------------

-- 지원서. 생성 · 제출은 BE-A, 승인 · 반려 · 전달 완료는 BE-B만 변경
CREATE TABLE application (
  id                     BIGINT       NOT NULL AUTO_INCREMENT,
  student_id             BIGINT       NOT NULL,
  recommendation_item_id BIGINT       NOT NULL,
  job_posting_id         BIGINT       NOT NULL,
  status                 VARCHAR(20)  NOT NULL DEFAULT 'GENERATING'
                         COMMENT 'GENERATING | DRAFT | SUBMITTED | APPROVED | REJECTED | DELIVERED | FAILED',
  submitted_at           DATETIME     NULL,
  reviewed_at            DATETIME     NULL,
  delivered_at           DATETIME     NULL,
  reject_note            TEXT         NULL,
  created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_item (recommendation_item_id),
  KEY idx_app_status (status),
  CONSTRAINT fk_app_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
  CONSTRAINT fk_app_item FOREIGN KEY (recommendation_item_id) REFERENCES recommendation_item (id),
  CONSTRAINT fk_app_job FOREIGN KEY (job_posting_id) REFERENCES job_posting (id)
) COMMENT '지원서';

-- 지원서 본문 (이력서 + 자기소개서, 문장별 근거). 형식 주인 AI-A
CREATE TABLE application_document (
  application_id     BIGINT       NOT NULL,
  document           JSON         NOT NULL COMMENT '서류 JSON (docs/API.md 참고)',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (application_id),
  CONSTRAINT fk_doc_app FOREIGN KEY (application_id) REFERENCES application (id) ON DELETE CASCADE
) COMMENT '지원서 본문';

-- ---------------------------------------------------------------------
-- 5. 상태 · 코디네이터 (S12 · S13)
-- ---------------------------------------------------------------------

-- 수료생 상태 (규칙으로 계산해 저장. 응답이 생길 때 + 하루 1회 무응답 판정)
CREATE TABLE student_status (
  student_id         BIGINT       NOT NULL,
  status             VARCHAR(20)  NOT NULL COMMENT 'EMPLOYED | SEEKING | NEED_HELP | CHECK_REQUIRED',
  sub_status         VARCHAR(20)  NULL     COMMENT 'SEEKING일 때: APPLYING(기관 추천 지원 중) | WAITING(패스 후 대기) | OTHER(기타 사유)',
  reason             VARCHAR(200) NULL     COMMENT '판정 근거 한 줄. 예: 패스 사유 ALREADY_EMPLOYED',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (student_id),
  KEY idx_status (status, sub_status),
  CONSTRAINT fk_status_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '수료생 상태';

-- 통화 · 상담 기록 (C1 전화 확인, C3)
CREATE TABLE contact_log (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  student_id         BIGINT       NOT NULL,
  contact_type       VARCHAR(20)  NOT NULL COMMENT 'PHONE | MESSAGE | MEETING',
  note               TEXT         NULL,
  created_by         VARCHAR(50)  NOT NULL DEFAULT 'coordinator' COMMENT 'P0는 로그인 없음',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_contact_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) COMMENT '통화 · 상담 기록';

-- =====================================================================
-- 상태 판정 규칙 (S12, BE-B 구현) — 위에서부터 먼저 맞는 것을 적용
--   1. EMPLOYED       : 패스 사유가 ALREADY_EMPLOYED인 응답이 있음
--   2. NEED_HELP      : status = 'OPEN'인 help_request가 있음
--   3. CHECK_REQUIRED : last_response_at이 7일 이상 지남 (추천은 받았는데 응답 없음 포함)
--   4. SEEKING        : 그 외
--        APPLYING = SUBMITTED · APPROVED · DELIVERED 지원서가 있음
--        OTHER    = 가장 최근 패스 사유가 OTHER
--        WAITING  = 나머지
-- =====================================================================
