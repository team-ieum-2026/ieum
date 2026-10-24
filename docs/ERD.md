# 이음 P0 ERD

`db/schema.sql` 기준입니다. GitHub에서 이 파일을 열면 다이어그램으로 보입니다. 스키마가 바뀌면 BE-B가 이 파일도 함께 고칩니다.

```mermaid
erDiagram
    course ||--o{ student : "수강"
    student ||--o{ student_education : "학력"
    student ||--o{ student_career : "경력"
    student ||--o{ student_certificate : "자격증"
    student ||--o{ student_project : "프로젝트"
    student ||--o| student_cover_letter : "자기소개서 원본"
    student ||--o| competency_profile : "역량 프로필 (S2)"
    student ||--o| student_status : "상태 (S12)"
    student ||--o{ recommendation : "추천 묶음 (S3)"
    recommendation ||--|{ recommendation_item : "Top-3"
    job_posting ||--o{ recommendation_item : "추천된 공고"
    student ||--o{ help_request : "도움 요청 (S11)"
    recommendation_item ||--o{ help_request : "어느 공고에서"
    recommendation_item ||--o| application : "지원 (S7)"
    student ||--o{ application : "지원서"
    job_posting ||--o{ application : "지원 공고"
    application ||--|| application_document : "서류 본문"
    student ||--o{ contact_log : "통화 · 상담"
    company_info }o..o{ job_posting : "company_name으로 연결"

    course {
        BIGINT id PK
        VARCHAR name
        VARCHAR cohort
        VARCHAR campus
        DATE started_on
        DATE ended_on
        JSON curriculum_skills
    }
    student {
        BIGINT id PK
        BIGINT course_id FK
        VARCHAR name
        VARCHAR email
        VARCHAR github_url
        VARCHAR desired_job
        VARCHAR preferred_region
        VARCHAR military_status "NULL 허용"
        JSON skills "직접 입력 기술"
        DATE completed_on
        DATETIME last_response_at "1주 무응답 판정"
    }
    student_education {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR school_name
        VARCHAR major
        VARCHAR degree
        VARCHAR status
        DATE graduated_on
        VARCHAR source "REGISTERED | USER"
    }
    student_career {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR company_name
        VARCHAR position
        DATE started_on
        DATE ended_on
        VARCHAR source
    }
    student_certificate {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR name
        VARCHAR issuer
        DATE acquired_on
        VARCHAR source
    }
    student_project {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR title
        TEXT role_note
        MEDIUMTEXT readme
        JSON tech_stack
        VARCHAR portfolio_file_url
        TINYINT include_in_apply
    }
    student_cover_letter {
        BIGINT student_id PK, FK
        MEDIUMTEXT content
    }
    competency_profile {
        BIGINT student_id PK, FK
        JSON profile "형식 주인 AI-A"
        CHAR source_hash
        DATETIME extracted_at
    }
    job_posting {
        BIGINT id PK
        VARCHAR source "SEOUL_API | PARTNER"
        CHAR external_id UK "해시로 생성"
        VARCHAR company_name
        VARCHAR title
        VARCHAR job_code "IT 직종 필터"
        MEDIUMTEXT description
        DATE deadline_on
        TINYINT until_filled "채용시까지"
        TINYINT is_active
        CHAR content_hash
        JSON raw "API 원본"
    }
    company_info {
        BIGINT id PK
        VARCHAR company_name UK
        VARCHAR status "FOUND | PARTIAL | NOT_FOUND"
        JSON info
    }
    recommendation {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR trigger_type "VISIT | REFRESH | BATCH"
        DATETIME created_at
    }
    recommendation_item {
        BIGINT id PK
        BIGINT recommendation_id FK
        BIGINT job_posting_id FK
        TINYINT rank_no "1~3"
        JSON reasons
        VARCHAR response "NONE | APPLY | PASS | HELP"
        VARCHAR pass_reason
        TEXT pass_note "비고"
        DATETIME responded_at
    }
    help_request {
        BIGINT id PK
        BIGINT student_id FK
        BIGINT recommendation_item_id FK
        VARCHAR contact_method
        TEXT memo
        VARCHAR status "OPEN | CONTACTED | CLOSED"
    }
    application {
        BIGINT id PK
        BIGINT student_id FK
        BIGINT recommendation_item_id FK, UK
        BIGINT job_posting_id FK
        VARCHAR status "GENERATING ~ DELIVERED"
        DATETIME submitted_at
        DATETIME reviewed_at
        DATETIME delivered_at
        TEXT reject_note
    }
    application_document {
        BIGINT application_id PK, FK
        JSON document "형식 주인 AI-A"
    }
    student_status {
        BIGINT student_id PK, FK
        VARCHAR status "EMPLOYED | SEEKING | NEED_HELP | CHECK_REQUIRED"
        VARCHAR sub_status "APPLYING | WAITING | OTHER"
        VARCHAR reason
    }
    contact_log {
        BIGINT id PK
        BIGINT student_id FK
        VARCHAR contact_type
        TEXT note
        DATETIME created_at
    }
```

## 테이블별 주인과 쓰는 쪽

| 테이블 | 만드는 쪽 (쓰기) | 읽는 쪽 | 화면 |
| --- | --- | --- | --- |
| course, student, student_education, student_career, student_certificate, student_project, student_cover_letter | BE-B (시드 · 프로필 수정) | BE-A, AI | M6, M8, C2, C3 |
| competency_profile | BE-B (AI-B 추출 결과 저장) | BE-A, C3 | M8, C3 |
| job_posting | BE-A (일 배치) | AI-B 색인 | M1, M2 |
| company_info | BE-A (AI-B 수집 결과 저장) | AI-A 서류 생성 | M5, M6-1 |
| recommendation, recommendation_item | BE-A | BE-B 상태 판정 | M1, M2, M3, M7, C3 |
| help_request | BE-B | - | M4, C1 |
| application | BE-A 생성 · 제출 / BE-B 승인 · 반려 · 전달 | 둘 다 | M5, M6, M7, C1, C4 |
| application_document | BE-A (AI-A 생성 결과 저장 · 수정) | BE-B | M6, M6-1, M6-2, C4 |
| student_status | BE-B (규칙 계산) | - | C1, C2, C3 |
| contact_log | BE-B | - | C1, C3 |

`company_info`와 `job_posting`은 외래키 없이 `company_name`으로 연결합니다. 공고 API의 기업명 표기가 일정하지 않을 수 있어서, 시연 기업만 수집해 두는 P0에서는 이름 일치로 충분합니다.
