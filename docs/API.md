# 이음 P0 API 명세

초안: PM · BE API 확정: BE-A · BE-B · AI API와 JSON 형식 확정: AI-A
화면 번호(R0, M1~M8, C1~C4)는 와이어프레임, 흐름 번호(S1~S13)는 흐름도와 같습니다.

---

## 1. 공통 JSON 규칙

모든 서버(BE · AI)가 같은 규칙을 따릅니다.

| 항목 | 규칙 | 예시 |
| --- | --- | --- |
| 키 이름 | camelCase | `studentId`, `deadlineOn` |
| 상태 · 종류 값 | UPPER_SNAKE 문자열 | `"PASS"`, `"ALREADY_EMPLOYED"` |
| 날짜 | `YYYY-MM-DD` | `"2026-10-31"` |
| 날짜 + 시각 | ISO 8601, 서울 시간대 | `"2026-10-24T09:00:00+09:00"` |
| ID | 숫자(Long) | `"id": 12` |
| 값 없음 | 키를 빼지 않고 `null` | `"passNote": null` |
| 목록 | 빈 목록은 `[]` | `"items": []` |
| 날짜 필드 이름 | 날짜는 `~On`, 시각은 `~At` | `completedOn`, `respondedAt` |

### 성공 응답

```json
{ "data": { ... } }
```

목록은 `items`와 `total`을 둡니다.

```json
{ "data": { "items": [ ... ], "total": 137 } }
```

### 실패 응답

HTTP 상태 코드(400, 404, 409, 500)와 함께 보냅니다. `code`는 UPPER_SNAKE, `message`는 화면에 그대로 띄워도 되는 한국어 문장입니다.

```json
{ "error": { "code": "STUDENT_NOT_FOUND", "message": "수료생 정보를 찾을 수 없어요." } }
```

| HTTP | 언제 | code 예시 |
| --- | --- | --- |
| 400 | 요청 값이 잘못됨 | `INVALID_REQUEST`, `PASS_REASON_REQUIRED` |
| 404 | 대상이 없음 · 없는 경로 | `STUDENT_NOT_FOUND`, `APPLICATION_NOT_FOUND`, `NOT_FOUND`(없는 경로) |
| 409 | 지금 상태에서 할 수 없음 | `ALREADY_RESPONDED`, `INVALID_STATUS` |
| 500 | 서버 오류 · AI 서버 실패 | `INTERNAL_ERROR`, `AI_SERVER_ERROR` |

### 공통 코드 값

| 이름 | 값 |
| --- | --- |
| 응답 `response` | `NONE` · `APPLY` · `PASS` · `HELP` |
| 패스 사유 `passReason` | `ALREADY_EMPLOYED`(이미 취업했어요) · `JOB_MISMATCH`(직무가 맞지 않아요) · `CONDITION_MISMATCH`(지역이나 근무 조건이 안 맞아요) · `SKILL_GAP`(요구 역량이 부족한 것 같아요) · `TAKING_BREAK`(지금은 구직을 잠시 쉬고 있어요) · `OTHER`(기타) |
| 연락 방법 `contactMethod` | `SMS` · `PHONE` · `EMAIL` |
| 지원서 상태 `status` | `GENERATING` · `DRAFT` · `SUBMITTED` · `APPROVED` · `REJECTED` · `DELIVERED` · `FAILED` |
| 수료생 상태 `status` | `EMPLOYED` · `SEEKING` · `NEED_HELP` · `CHECK_REQUIRED` |
| 구직 중 세부 `subStatus` | `APPLYING` · `WAITING` · `OTHER` |
| 출처 태그 `source` | `AI`(AI 작성 · 근거 연결) · `REGISTERED`(등록 자료) · `USER`(직접 입력) |
| 병역 `militaryStatus` | `SERVED` · `NOT_SERVED` · `EXEMPT` · `NOT_APPLICABLE` · `null` |

P0는 로그인이 없습니다. 수료생 화면은 studentId를 바꿔 가며 씁니다. 기본값은 FE `.env.local`의 `NEXT_PUBLIC_DEMO_STUDENT_ID`이고, 주소 뒤에 `?studentId=7`을 붙이면 그 수료생으로 봅니다. 개발 중에는 평가용 수료생(id 1~20)을 바꿔 가며 추천이 사람마다 다르게 나오는지 확인합니다.

---

## 2. BE API (Spring Boot, `/api`)

### 2-1. 수료생 흐름 — BE-A

| 화면 | 메서드 · 경로 | 설명 | 흐름 |
| --- | --- | --- | --- |
| M1 | `GET /api/students/{studentId}/recommendations/current` | 현재 추천 3건. 없으면 AI 매칭을 돌려 새로 만듦 | S3 · S4 |
| M1 | `POST /api/students/{studentId}/recommendations/refresh` | 새로 추천받기. 응답한 공고는 제외 | S3 |
| M2 | `GET /api/recommendation-items/{itemId}` | 공고 상세 + 추천 근거 전체 | S5 |
| M2 · M3 | `POST /api/recommendation-items/{itemId}/response` | 지원 또는 패스 응답 | S5 · S6 |
| M5 | `GET /api/applications/{applicationId}/progress` | 서류 생성 진행 상태 (FE가 2초마다 조회) | S7 |
| M6 · M6-1 | `GET /api/applications/{applicationId}` | 서류 전체 (이력서 + 자기소개서 + 근거) | S8 |
| M6 · M6-1 · M6-2 | `PATCH /api/applications/{applicationId}/document` | 문장 수정 · 섹션 항목 추가 · 수정 · 삭제 | S8 |
| M6 | `POST /api/applications/{applicationId}/submit` | 담당자에게 검토 요청 (상태값은 `SUBMITTED`) | S8 |
| M7 | `GET /api/students/{studentId}/applications` | 지원 현황 목록 + 이번 추천의 다른 공고 응답 | S9 · S10 |
| - | `POST /api/admin/jobs/sync` | 공고 배치 수동 실행 (시연 · 개발용, 인증 없음, 15초~2분). 필드 매핑은 `docs/JOB_API.md`. 응답은 아래 | S3 |
| - | `POST /api/admin/jobs/reindex` | AI 전체 재색인 (개발용, 인증 없음). AI 서버를 처음 연결할 때 한 번 실행. 응답은 아래 | S3 |

#### `POST /api/admin/jobs/sync` (공고 배치)

요청 본문 없음. API 호출이 실패하면 오류가 아니라 `status: "SKIPPED"`로 응답하고 기존 공고를 그대로 씁니다.

```json
{ "data": { "status": "DONE", "message": "공고 동기화를 마쳤어요.", "fetchedCount": 41191, "itCount": 105,
  "insertedCount": 105, "updatedCount": 0, "unchangedCount": 0, "deactivatedCount": 3,
  "indexRequestedCount": 105,
  "indexResult": { "indexedCount": 105, "skippedCount": 0, "removedCount": 3, "failedBatchCount": 0 },
  "syncedAt": "2026-10-25T23:43:20+09:00" } }
```

- `indexResult`는 AI 색인 요청을 20건씩 나눠 보낸 응답을 더한 값입니다. 보낼 공고도 지울 공고도 없으면 `null`입니다. `failedBatchCount`는 실패한 묶음 수이고, 실패한 묶음은 다음 배치에서 다시 보내지 않으니 `POST /api/admin/jobs/reindex`로 재시도합니다.
- 배치가 끝나면 추천이 없거나 0건이거나 공고가 모두 꺼진 수료생의 추천을 백그라운드로 미리 만듭니다(`trigger_type = BATCH`). 응답은 이 작업을 기다리지 않습니다.

#### `POST /api/admin/jobs/reindex` (AI 전체 재색인)

배치는 새로 들어오거나 바뀐 공고만 AI에 보내므로, AI 색인이 비어 있으면(AI 서버를 처음 연결 · 색인 파일 삭제) 배치를 돌려도 채워지지 않습니다. 이때 한 번 실행합니다. 활성 공고 전체를 20건씩 나눠 보내고, 꺼진 공고 id는 첫 요청의 `removedJobPostingIds`에 싣습니다. AI는 `contentHash`가 같으면 건너뛰므로 여러 번 불러도 됩니다.

```json
{ "data": { "status": "DONE", "message": "AI 색인을 다시 만들었어요.", "indexRequestedCount": 143, "removeRequestedCount": 18,
  "indexResult": { "indexedCount": 143, "skippedCount": 0, "removedCount": 0, "failedBatchCount": 0 },
  "reindexedAt": "2026-10-28T09:00:00+09:00" } }
```

- 배치나 재색인이 실행 중이거나 활성 공고가 없으면 `status: "SKIPPED"`, 나머지 값은 0 · `null`
- 끝나면 배치와 같이 0건 · 빈 추천을 백그라운드로 다시 만듭니다.
- `AI_MOCK=false`로 바꿀 때 순서: AI 서버 실행 → `POST /api/admin/jobs/reindex` → M1 확인

#### `GET /api/students/{studentId}/recommendations/current` (M1)

```json
{
  "data": {
    "recommendationId": 31,
    "createdAt": "2026-10-24T09:00:00+09:00",
    "basis": "PROFILE_LATEST",
    "items": [
      {
        "itemId": 91,
        "rank": 1,
        "response": "NONE",
        "job": {
          "jobPostingId": 501,
          "companyName": "(주)온다랩",
          "title": "백엔드 인턴 (AI 서비스팀)",
          "location": "서울 성동구",
          "employmentType": "인턴",
          "career": "신입",
          "deadlineOn": "2026-10-31",
          "untilFilled": false,
          "dDay": 7
        },
        "reasonSummary": [
          "모아요 프로젝트의 Spring Boot API 경험이 주요 업무와 맞아요",
          "LLM API 연동 경험이 우대사항에 해당해요"
        ]
      }
    ]
  }
}
```

- 맞는 공고가 없으면 `items: []`입니다. 이 결과도 0건 추천으로 저장되어 `recommendationId`가 있습니다. 프로필이 아직 없을 때만 저장하지 않고 `recommendationId: null`로 응답합니다. 0건은 프로필이 바뀌거나 다음 공고 배치가 돌 때까지 그대로 씁니다.
- `untilFilled: true`면 "채용 시 마감"이라 표시된 날짜 전에도 마감될 수 있습니다. 카드에는 `D-7 · 채용 시 마감`처럼 함께 표시합니다.
- M2 공고 상세(`GET /api/recommendation-items/{itemId}`)의 전체 응답 형식은 `frontend/public/mock/M2_recommendation_item_detail.json`을 따릅니다. 공고 원문 링크는 API에 없어 `homepageUrl`은 `null`이고, 출처 문구 `sourceLabel`을 표시합니다.
  - `summary`: LLM 없이 공고 본문 `[내용]`의 앞 2줄(제목 · 링크 줄 제외, 60자에서 자름). 없으면 `[]`
  - `reasons[].evidence`: AI는 `{ refType, refId }`만 주므로 BE가 수료생 등록 자료에서 `label` · `quote`를 채웁니다(예: `PROJECT` → "모아요 · 프로젝트" / "Java, Spring Boot · 6주 · 4명"). 찾을 수 없는 근거는 빼고, 근거가 없으면 `evidence: []`
  - 없는 항목이면 404 `RECOMMENDATION_ITEM_NOT_FOUND`

#### `POST /api/recommendation-items/{itemId}/response` (M2 지원 · M3 패스)

도움 요청은 이 API가 아니라 `POST /api/help-requests`(BE-B)를 씁니다.

```json
// 지원
{ "response": "APPLY" }
// 패스
{ "response": "PASS", "passReason": "OTHER", "passNote": "경기도 쪽 공고도 보고 싶어요" }
```

```json
// 지원 응답 → M5로 이동
{ "data": { "itemId": 91, "response": "APPLY", "applicationId": 7 } }
// 패스 응답 → M1로 이동
{ "data": { "itemId": 92, "response": "PASS", "applicationId": null } }
```

- `PASS`인데 `passReason`이 없으면 400 `PASS_REASON_REQUIRED`. 목록에 없는 사유, `APPLY` · `PASS` 외의 `response`, 1,000자 넘는 `passNote`는 400 `INVALID_REQUEST`
- 이미 응답한 항목이면 409 `ALREADY_RESPONDED`. 없는 항목이면 404 `RECOMMENDATION_ITEM_NOT_FOUND`
- `APPLY`면 `application`을 `GENERATING`으로 만들고 그 번호를 돌려줍니다. 서류 생성은 BE-A-07이 이어서 합니다.
- 응답이 생기면 `student.last_response_at` 갱신 + `RecommendationRespondedEvent` 발행. 상태 재계산(BE-B-06)과 서류 생성(BE-A-07)은 이 이벤트를 저장 확정 뒤(`AFTER_COMMIT`) 받아 처리합니다.

#### `GET /api/applications/{applicationId}/progress` (M5)

```json
{
  "data": {
    "applicationId": 7,
    "status": "GENERATING",
    "steps": [
      { "key": "COMPANY_INFO", "state": "DONE", "note": "인재상 확인 · 최근 뉴스는 찾지 못함" },
      { "key": "COVER_LETTER", "state": "RUNNING", "note": null },
      { "key": "RESUME", "state": "WAITING", "note": null }
    ]
  }
}
```

`status`가 `DRAFT`가 되면 FE는 M6로 이동합니다. `FAILED`면 다시 시도 버튼을 보여 줍니다.

#### `GET /api/applications/{applicationId}` (M6 · M6-1)

```json
{
  "data": {
    "applicationId": 7,
    "status": "DRAFT",
    "job": { "jobPostingId": 501, "companyName": "(주)온다랩", "title": "백엔드 인턴 (AI 서비스팀)" },
    "document": { "…": "아래 '3. 핵심 JSON 형식'의 서류 JSON" }
  }
}
```

- `status`가 `GENERATING` · `FAILED`이면 200으로 응답하고 `document`만 `null`입니다(다른 키는 그대로).
- 없는 지원서면 404 `APPLICATION_NOT_FOUND`

#### `PATCH /api/applications/{applicationId}/document` (M6 · M6-1 · M6-2)

한 번에 하나의 변경만 보냅니다. 수정된 항목의 `source`는 서버가 `USER`로 바꿉니다.

```json
// 자기소개서 문장 수정 (M6-1)
{ "op": "UPDATE_SENTENCE", "sentenceId": "cl-2", "text": "고친 문장" }

// 섹션 항목 추가 (M6-2, 자격증 예시)
{ "op": "ADD_ITEM", "section": "CERTIFICATES",
  "item": { "name": "SQLD", "issuer": "한국데이터산업진흥원", "acquiredOn": "2026-09-01" },
  "saveToProfile": true }

// 섹션 항목 수정 · 삭제
{ "op": "UPDATE_ITEM", "section": "EDUCATION", "itemId": "edu-1", "item": { "…": "…" }, "saveToProfile": false }
{ "op": "DELETE_ITEM", "section": "CERTIFICATES", "itemId": "cert-2", "saveToProfile": false }
```

- `section`: `BASIC` · `EDUCATION` · `TRAINING` · `CAREER` · `MILITARY` · `CERTIFICATES` · `SKILLS` · `PROJECTS` · `PORTFOLIO` · `COVER_LETTER`
- 섹션과 저장 위치: `BASIC` → `student` · `EDUCATION` → `student_education` · `TRAINING` → `course` (읽기 전용) · `CAREER` → `student_career` · `MILITARY` → `student.military_status` · `CERTIFICATES` → `student_certificate` · `SKILLS` → `student.skills` · `PROJECTS` · `PORTFOLIO` → `student_project` · `COVER_LETTER` → `student_cover_letter`
- `saveToProfile: true`면 BE-A가 BE-B의 프로필 서비스를 호출해 수료생 프로필에도 저장 → 다음 추천에 반영
- `PROJECTS`를 프로필에 저장할 때는 `title` · `periodText` · `teamSize` · `techStack`만 반영합니다(`PORTFOLIO`의 `include`는 `include_in_apply`). `bullets`는 공고마다 다르게 쓴 서류 전용 문장이라 프로필에 저장하지 않고, `student_project.readme`(README 원문)와 `role_note`(역할 기록)는 덮어쓰지 않습니다. AI 문장의 근거(`evidence.quote`)가 README 원문을 인용하기 때문입니다. README · 역할 기록은 M8 프로필에서만 고칩니다.
- 응답: 바뀐 서류 JSON 전체 `{ "data": { "document": { … } } }`

섹션별 예시:

```json
// 문장 수정: 자기소개서 · 기술 요약 · 프로젝트 문장 모두 UPDATE_SENTENCE
{ "op": "UPDATE_SENTENCE", "sentenceId": "sk-1", "text": "고친 기술 요약" }
{ "op": "UPDATE_SENTENCE", "sentenceId": "pj-1", "text": "고친 프로젝트 문장" }

// BASIC: 항목이 하나뿐이라 itemId는 null
{ "op": "UPDATE_ITEM", "section": "BASIC", "itemId": null, "item": { "email": "new@example.com" }, "saveToProfile": true }

// MILITARY: itemId null, 값은 value 하나
{ "op": "UPDATE_ITEM", "section": "MILITARY", "itemId": null, "item": { "value": "SERVED" }, "saveToProfile": true }

// SKILLS 태그: itemId null, 태그 전체 목록을 보냄
{ "op": "UPDATE_ITEM", "section": "SKILLS", "itemId": null, "item": { "tags": ["Java", "Spring Boot", "Redis"] }, "saveToProfile": true }

// PROJECTS: title · periodText · teamSize · role만 (bullets는 UPDATE_SENTENCE로)
{ "op": "UPDATE_ITEM", "section": "PROJECTS", "itemId": "prj-11", "item": { "role": "백엔드 · 배포" }, "saveToProfile": false }

// PORTFOLIO: include만 (fileName은 바꿀 수 없음)
{ "op": "UPDATE_ITEM", "section": "PORTFOLIO", "itemId": "pf-11", "item": { "include": false }, "saveToProfile": true }

// EDUCATION · CAREER · CERTIFICATES: 바뀐 필드만
{ "op": "UPDATE_ITEM", "section": "CAREER", "itemId": "car-1", "item": { "endedOn": null }, "saveToProfile": true }
```

- `UPDATE_ITEM`의 `item`에는 바뀐 필드만 보냅니다. 키가 없으면 그대로 두고, `null`이면 비웁니다.
- `BASIC` · `MILITARY` · `SKILLS`는 `itemId: null`이고 `ADD_ITEM` · `DELETE_ITEM`을 쓰지 않습니다.
- 오류: `DRAFT`가 아니면 409 `INVALID_STATUS` · 섹션에 맞지 않는 op나 필드면 400 `INVALID_REQUEST` · 없는 지원서면 404 `APPLICATION_NOT_FOUND`

#### `POST /api/applications/{applicationId}/submit` (M6)

화면 버튼 문구는 "담당자에게 검토 요청"입니다. 요청 본문 없음. `DRAFT` → `SUBMITTED`, 응답 `{ "data": { "applicationId": 7, "status": "SUBMITTED" } }`. `DRAFT`가 아니면 409 `INVALID_STATUS`.

#### `GET /api/students/{studentId}/applications` (M7)

```json
{
  "data": {
    "applications": [
      { "applicationId": 7, "companyName": "(주)온다랩", "title": "백엔드 인턴 (AI 서비스팀)",
        "status": "SUBMITTED", "submittedAt": "2026-10-24T10:12:00+09:00", "userEditedCount": 1 }
    ],
    "otherItems": [
      { "itemId": 92, "companyName": "데이터웨이브(주)", "title": "주니어 데이터 엔지니어",
        "response": "PASS", "passReason": "JOB_MISMATCH" },
      { "itemId": 93, "companyName": "(주)서울에이아이랩", "title": "AI 서비스 기획 보조",
        "response": "NONE", "passReason": null }
    ]
  }
}
```

### 2-2. 프로필 · 도움 요청 — BE-B

| 화면 | 메서드 · 경로 | 설명 | 흐름 |
| --- | --- | --- | --- |
| M8 | `GET /api/students/{studentId}/profile` | 등록 자료 전체 + 역량 프로필 | S1 · S2 |
| M8 | `POST /api/students/{studentId}/profile/items` | 프로필 섹션 항목 추가 | S1 → S2 |
| M8 | `PATCH /api/students/{studentId}/profile/items/{itemId}` | 프로필 섹션 항목 수정 | S1 → S2 |
| M8 | `DELETE /api/students/{studentId}/profile/items/{itemId}?section=` | 프로필 섹션 항목 삭제 | S1 → S2 |
| M8 | `PUT /api/students/{studentId}/cover-letter` | 자기소개서 원본 수정 `{ "content": "…" }` | S1 → S2 |
| M2 → M4 | `POST /api/help-requests` | 도움 요청 생성 | S5 → S11 |
| M4 | `PATCH /api/help-requests/{helpRequestId}` | 연락 방법 · 메모 추가 | S11 |

- 프로필이 바뀌면 BE-B가 AI 서버 `POST /ai/profile/extract`를 호출해 역량 프로필을 다시 만듭니다. M8 상단의 "반영 완료" 배너는 응답의 `profileUpdatedAt`으로 띄웁니다.
- 프로필 항목 추가 · 수정 요청 본문은 서류 PATCH의 `section` · `item` 형식과 같습니다.

#### `POST /api/help-requests` (M2 도움 요청 버튼)

```json
{ "studentId": 1, "recommendationItemId": 93 }
```

```json
{ "data": { "helpRequestId": 4, "status": "OPEN" } }
```

#### `PATCH /api/help-requests/{helpRequestId}` (M4)

```json
{ "contactMethod": "SMS", "memo": "지원서를 같이 봐주셨으면 해요" }
```

### 2-3. 코디네이터 — BE-B

| 화면 | 메서드 · 경로 | 설명 | 흐름 |
| --- | --- | --- | --- |
| C1 | `GET /api/coordinator/dashboard` | 도움 요청, 상태별 집계, 할 일 3종 | S11 · S12 · S13 |
| C2 | `GET /api/coordinator/students?status=&subStatus=&q=&page=1&size=20` | 수료생 목록 | S12 |
| C3 | `GET /api/coordinator/students/{studentId}` | 수료생 상세 | S2 · S6 |
| C1 · C3 | `POST /api/coordinator/students/{studentId}/contact-logs` | 통화 · 상담 기록 `{ "contactType": "PHONE", "note": "…" }` | S12 |
| C1 | `PATCH /api/coordinator/help-requests/{helpRequestId}` | 도움 요청 처리 `{ "status": "CONTACTED" }` | S11 |
| C4 | `GET /api/coordinator/applications/{applicationId}` | 서류 + 검증 요약 | S9 |
| C4 | `POST /api/coordinator/applications/{applicationId}/approve` | 승인 (`SUBMITTED` → `APPROVED`) | S9 |
| C4 | `POST /api/coordinator/applications/{applicationId}/reject` | 반려 `{ "note": "…" }` (→ `REJECTED`, 코디네이터가 연락) | S9 |
| C4 | `POST /api/coordinator/applications/{applicationId}/deliver` | 전달 완료 (`APPROVED` → `DELIVERED`, 실제 전송 없음) | S10 |

#### `GET /api/coordinator/dashboard` (C1)

```json
{
  "data": {
    "asOf": "2026-10-24T09:00:00+09:00",
    "totalStudents": 137,
    "helpRequests": [
      { "helpRequestId": 4, "studentId": 12, "studentName": "박서연", "courseLabel": "AI Agent 4기",
        "createdAt": "2026-10-24T09:12:00+09:00", "jobTitle": "데이터웨이브 데이터 엔지니어",
        "contactMethod": "SMS", "memo": null }
    ],
    "statusSummary": {
      "EMPLOYED": 32,
      "SEEKING": { "total": 88, "APPLYING": 41, "WAITING": 28, "OTHER": 19 },
      "NEED_HELP": 5,
      "CHECK_REQUIRED": 12
    },
    "reviewQueue": [
      { "applicationId": 7, "studentName": "김민준", "jobTitle": "온다랩 백엔드 인턴", "submittedAt": "2026-10-24T10:12:00+09:00" }
    ],
    "callList": [
      { "studentId": 21, "studentName": "최유나", "lastResponseAt": "2026-10-14T18:00:00+09:00", "daysSinceResponse": 10 }
    ],
    "notesToCheck": [
      { "studentId": 33, "studentName": "서민재", "passNote": "부트캠프 하나 더 들을까 고민 중" }
    ]
  }
}
```

#### `GET /api/coordinator/applications/{applicationId}` (C4)

`GET /api/applications/{id}`의 응답에 검증 요약을 더한 형태입니다.

```json
{
  "data": {
    "applicationId": 7,
    "status": "SUBMITTED",
    "student": { "studentId": 1, "name": "김민준" },
    "job": { "jobPostingId": 501, "companyName": "(주)온다랩", "title": "백엔드 인턴 (AI 서비스팀)" },
    "document": { "…": "서류 JSON" },
    "verification": {
      "aiSentenceCount": 8,
      "aiSentenceWithEvidence": 8,
      "excludedSentenceCount": 2,
      "userEditedCount": 2,
      "companyInfoStatus": "PARTIAL"
    }
  }
}
```

---

## 3. 핵심 JSON 형식 — 주인 AI-A

DB의 JSON 컬럼과 API가 같은 형식을 씁니다. 형식 주인은 AI-A이며, 이 초안대로 개발을 시작합니다. 바꿀 때는 디스코드에 알리고 이 문서와 `ai/schemas/`를 함께 고칩니다.

### 3-1. 역량 프로필 (`competency_profile.profile`, S2)

```json
{
  "roles": ["백엔드 API 설계 · 구현", "LLM API 연동"],
  "skills": [
    { "name": "Spring Boot", "evidence": [{ "refType": "PROJECT", "refId": 11 }] },
    { "name": "Gemini API", "evidence": [{ "refType": "PROJECT", "refId": 12 }] }
  ],
  "achievements": [
    { "text": "목록 조회 응답 1.8초 → 0.4초 개선", "evidence": [{ "refType": "PROJECT", "refId": 11 }] }
  ],
  "certificates": ["정보처리기사", "SQLD"],
  "desiredJob": "백엔드 개발",
  "preferredRegion": "서울"
}
```

`refType`: `PROJECT`(README · 역할 기록) · `COVER_LETTER` · `CERTIFICATE` · `EDUCATION` · `CAREER`(경력) · `COURSE` · `COMPANY_INFO` · `JOB_POSTING`(공고 원문)

- `CAREER`의 `refId`는 `student_career.id`, `JOB_POSTING`의 `refId`는 `job_posting.id`입니다.

### 3-2. 근거 (`evidence`)

문장 하나에 근거 여러 개를 붙일 수 있습니다. 근거가 하나도 없는 AI 문장은 생성 단계에서 뺍니다.

```json
{
  "refType": "PROJECT",
  "refId": 11,
  "label": "모아요 README · 성능 개선",
  "quote": "목록 API에서 N+1 쿼리 발생 → fetch join 적용. 평균 응답 1.8s → 0.4s",
  "url": null,
  "collectedAt": null
}
```

기업 정보 근거는 `refType: "COMPANY_INFO"`, `url`과 `collectedAt`을 채웁니다.

### 3-3. 서류 (`application_document.document`, S7 · S8)

이력서와 자기소개서를 한 문서로 담습니다. 섹션 순서가 곧 M6 화면과 C4 화면의 순서입니다.

```json
{
  "companyInfoStatus": "PARTIAL",
  "companyInfoNote": "온다랩의 최근 뉴스를 찾지 못해 인재상만 반영했어요",
  "excludedSentenceCount": 2,
  "sections": [
    { "section": "BASIC", "source": "REGISTERED",
      "fields": { "name": "김민준", "email": "minjun.dev@example.com", "githubUrl": "github.com/minjun-dev", "desiredJob": "백엔드 개발" } },
    { "section": "EDUCATION",
      "items": [ { "itemId": "edu-1", "source": "REGISTERED", "schoolName": "○○대학교", "major": "경영학과", "degree": "BACHELOR", "status": "GRADUATED", "graduatedOn": "2025-02-28" } ] },
    { "section": "TRAINING",
      "items": [ { "itemId": "trn-1", "source": "REGISTERED", "name": "청년취업사관학교 AI Agent 과정", "campus": "동대문캠퍼스", "startedOn": "2026-03-02", "endedOn": "2026-08-29" } ] },
    { "section": "CAREER",
      "items": [ { "itemId": "car-1", "source": "REGISTERED", "companyName": "○○소프트", "position": "백엔드 인턴", "startedOn": "2025-07-01", "endedOn": "2025-08-31", "description": "사내 관리자 페이지 API 유지보수" } ] },
    { "section": "MILITARY", "value": null },
    { "section": "CERTIFICATES",
      "items": [
        { "itemId": "cert-1", "source": "REGISTERED", "name": "정보처리기사", "issuer": "한국산업인력공단", "acquiredOn": "2026-03-20" },
        { "itemId": "cert-2", "source": "USER", "name": "SQLD", "issuer": "한국데이터산업진흥원", "acquiredOn": "2026-09-01" }
      ] },
    { "section": "SKILLS", "source": "AI",
      "summary": [ { "sentenceId": "sk-1", "text": "Spring Boot · MySQL로 인증부터 조회 API까지 직접 설계하고 구현", "source": "AI", "evidence": [ { "…": "근거" } ] } ],
      "tags": ["Java", "Spring Boot", "MySQL", "FastAPI", "Gemini API"] },
    { "section": "PROJECTS",
      "items": [
        { "itemId": "prj-11", "title": "모아요 · 스터디 모집 웹서비스", "periodText": "6주", "teamSize": 4, "role": "백엔드",
          "bullets": [ { "sentenceId": "pj-1", "text": "모집글 CRUD 등 REST API 12개 설계 · 구현", "source": "AI", "evidence": [ { "…": "근거" } ] } ] }
      ] },
    { "section": "PORTFOLIO",
      "items": [ { "itemId": "pf-11", "fileName": "모아요_포트폴리오.pdf", "include": true } ] },
    { "section": "COVER_LETTER",
      "sentences": [
        { "sentenceId": "cl-1", "text": "온다랩이 말하는 '끝까지 책임지는 개발자'처럼, …", "source": "AI",
          "evidence": [ { "refType": "COMPANY_INFO", "refId": 3, "label": "온다랩 채용 페이지 · 인재상", "quote": "끝까지 책임지는 사람", "url": "https://…", "collectedAt": "2026-10-24T08:00:00+09:00" },
                        { "refType": "COVER_LETTER", "refId": 1, "label": "등록 자기소개서 2문단", "quote": "…", "url": null, "collectedAt": null } ] },
        { "sentenceId": "cl-4", "text": "이 경험을 바탕으로 …", "source": "USER", "evidence": [] }
      ] }
  ]
}
```

- `source`가 `AI`인 문장은 `evidence`가 1개 이상이어야 합니다. `USER`는 비어 있어도 됩니다.
- `companyInfoStatus`: `FOUND` · `PARTIAL` · `NOT_FOUND`. `NOT_FOUND`면 화면에 "공고 외 정보 확인 불가"를 표시합니다.

---

## 4. AI 서버 API (FastAPI, `/ai`) — BE만 호출

FE는 AI 서버를 직접 부르지 않습니다. 요청 · 응답 형식은 위 공통 규칙을 따릅니다.

| 메서드 · 경로 | 담당 | 호출하는 쪽 | 설명 | 흐름 |
| --- | --- | --- | --- | --- |
| `POST /ai/profile/extract` | AI-B | BE-B | 등록 자료 → 역량 프로필 JSON | S2 |
| `POST /ai/matching/index` | AI-B | BE-A (일 배치 후) | 공고 임베딩 색인 갱신 (`contentHash`가 바뀐 공고만) | S3 |
| `POST /ai/matching/recommend` | AI-B | BE-A | 프로필 → Top-3 공고 + 추천 근거 | S3 · S4 |
| `POST /ai/company/collect` | AI-B | BE-A (시연 전 1회) | 기업 정보 웹 검색 수집 → `company_info` 형식 | S7 |
| `POST /ai/document/draft` | AI-A | BE-A | 등록 자료 + 프로필 + 공고 + 기업 정보 → 서류 JSON | S7 |
| `GET /ai/health` | AI-A | 누구나 | 서버 상태 확인 `{ "data": { "status": "OK" } }` | - |

#### `POST /ai/matching/index`

```json
// 요청 (20건씩 나눠 보냄. removedJobPostingIds는 첫 요청에만 싣고 나머지는 [])
{ "jobs": [ { "jobPostingId": 613, "contentHash": "…64자", "companyName": "…", "title": "…",
              "jobCategory": "응용 소프트웨어 개발자", "location": "서울 구로구", "career": "신입",
              "education": "…MIN_EDUBG 원문", "description": "[직무] …" } ],
  "removedJobPostingIds": [501, 502] }
// 응답
{ "data": { "indexedCount": 20, "skippedCount": 0, "removedCount": 2 } }
```

- `jobs`: 새로 들어오거나 `contentHash`가 바뀌거나 다시 켜진 활성 공고. `contentHash`는 BE가 계산한 값을 그대로 씁니다. `education`은 공고 API `MIN_EDUBG` 원문이고 없으면 `null`입니다.
- `removedJobPostingIds`: 이번 배치에서 꺼진 공고(마감 · 목록에서 사라짐 · 개발용 임시 공고). AI 색인에서 지워 추천되지 않게 합니다.

#### `POST /ai/matching/recommend`

```json
// 요청
{ "studentId": 1, "profile": { "…": "역량 프로필" }, "excludeJobPostingIds": [501, 502], "topK": 3 }
// 응답
{ "data": { "items": [
  { "jobPostingId": 503, "rank": 1, "reasons": [
      { "text": "모아요 프로젝트의 Spring Boot API 경험이 주요 업무와 맞아요", "evidence": [ { "refType": "PROJECT", "refId": 11 } ] } ] }
] } }
```

- 리랭커 때문에 1명당 30~60초 걸릴 수 있습니다. BE의 AI 호출 응답 대기 시간은 120초입니다.
- 맞는 공고가 없으면 `items: []`로 응답합니다. BE는 이것도 0건 추천으로 저장합니다.

#### `POST /ai/document/draft`

```json
// 요청
{
  "applicationId": 7,
  "student": { "…": "기본정보 · 학력 · 과정 · 자격증 · 프로젝트 원문 · 자기소개서 원본" },
  "profile": { "…": "역량 프로필" },
  "job": { "…": "공고 한 건" },
  "companyInfo": { "…": "company_info 한 건 또는 null" }
}
// 응답
{ "data": { "document": { "…": "서류 JSON" } } }
```

- 서류 생성은 20~40초 걸릴 수 있습니다. BE-A는 비동기로 호출하고 `application.status`를 `GENERATING` → `DRAFT`(또는 `FAILED`)로 바꿉니다. M5는 진행 상태 API를 조회합니다.
- AI 서버 실패는 BE가 500 `AI_SERVER_ERROR`로 FE에 전달합니다.

---

## 5. 시드 · 목업

- `db/schema.sql`: 테이블 생성 (주인 BE-B)
- `db/seed.sql`: 수료생 50명 시드 (PM 작성, 주인 BE-B). 평가용 20 · 명단용 30. 공고는 BE-A 배치로 받음
- `frontend/public/mock/`: FE가 첫 주에 쓰는 목업 응답 (주인 PM, 형식이 바뀌면 그 API 담당이 함께 고침). 파일 이름은 `화면번호_설명.json` (예: `M1_recommendations_current.json`)
- 이 문서에 응답 예시가 짧게만 있는 API(M2 공고 상세, M8 프로필, C2 목록, C3 상세)는 목업 파일이 전체 응답 형식입니다. BE는 목업과 같은 모양으로 응답합니다.
