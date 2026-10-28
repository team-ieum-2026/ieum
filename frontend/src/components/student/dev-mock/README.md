# 로컬 JSON 목업 서버 (FE-A 개발 도구)

Node 내장 HTTP 서버로 실제 BE 없이 프런트의 `/api/*` 요청을 검증한다.
화면에서 이 디렉터리의 모듈을 import하지 않는다. 라이브러리 설치는 필요 없다.

저장소 루트 `D:\ieum\ieum`에서 실행:

```powershell
node frontend/src/components/student/dev-mock/server.mjs
```

기본 주소는 `http://localhost:4000`이며 루프백 주소에만 바인딩한다.
실제 BE의 8080과 구분하기 위해 목업은 4000을 사용한다.
4000이 사용 중이면 기존 프로세스를 확인한다. 대체 포트는 마지막 인자로 지정한다.

```powershell
node frontend/src/components/student/dev-mock/server.mjs 4001
```

다른 터미널에서 `frontend/.env.local`의 해당 키만 설정하고 FE를 재시작한다.
기존 파일의 다른 값은 보존한다.

| 모드 | NEXT_PUBLIC_USE_MOCK | NEXT_PUBLIC_API_BASE_URL |
| --- | --- | --- |
| 기존 정적 목업 | `true` | 사용하지 않음 |
| HTTP 목업 | `false` | `http://localhost:4000` (대체 포트 사용 시 변경) |
| 실제 BE | `false` | 실제 BE 주소 |

```powershell
cd frontend
npm run dev
```

이 프로젝트는 Turbopack 규칙으로 Tailwind를 처리하므로 `--webpack`을 붙이지 않는다.
실행 터미널에 `NEXT_PUBLIC_USE_MOCK` 등이 이미 설정돼 있으면 `.env.local`보다 우선한다.
해당 프로세스 변수를 제거하거나 원하는 모드와 일치시킨다. `NEXT_PUBLIC_*`는 비밀 값에 사용하지 않는다.
프런트 공통 API 계층은 이미 이 전환을 지원한다. HTTP 모드는 정적 목업으로 실패를 대체하지 않는다.

## API / 기존 목업 매핑

모든 성공은 `{data:...}`, 실패는 HTTP 오류와 `{error:{code,message}}`로 반환한다.
표의 `{studentId}`, `{itemId}`, `{applicationId}` 등은 실제 생성된 ID로 바꾼다.

| 메서드 · 경로 | 초기 데이터 / 응답 구조 |
| --- | --- |
| GET /api/students/{studentId}/recommendations/current | M1_recommendations_current.json |
| POST /api/students/{studentId}/recommendations/refresh | M1_recommendations_refresh.json |
| GET /api/recommendation-items/{itemId} | M2_recommendation_item_detail.json |
| POST /api/recommendation-items/{itemId}/response | M2_response_apply.json, M3_response_pass.json |
| POST /api/help-requests | M4_help_request_create.json |
| PATCH /api/help-requests/{helpRequestId} | M4_help_request_update.json |
| GET /api/applications/{applicationId}/progress | M5_application_progress_generating.json, M5_application_progress_done.json |
| GET /api/applications/{applicationId} | M6_application_document.json |
| PATCH /api/applications/{applicationId}/document | M6_application_document.json의 document (응답은 `{data:{document:...}}`) |
| POST /api/applications/{applicationId}/submit | M6_submit.json |
| GET /api/students/{studentId}/applications | M7_student_applications.json |
| GET /api/students/{studentId}/profile | M8_student_profile.json |
| POST /api/students/{studentId}/profile/items | M8_student_profile.json, 요청은 section/item |
| PATCH /api/students/{studentId}/profile/items/{itemId} | M8_student_profile.json, 요청은 section/item |
| DELETE /api/students/{studentId}/profile/items/{itemId}?section=... | M8_student_profile.json |
| PUT /api/students/{studentId}/cover-letter | M8_student_profile.json, 요청은 content |
| GET /api/coordinator/dashboard | C1_coordinator_dashboard.json |
| PATCH /api/coordinator/help-requests/{helpRequestId} | C1_help_request_update.json |
| GET /api/coordinator/students | C2_coordinator_students.json |
| GET /api/coordinator/students/{studentId} | C3_coordinator_student_detail.json |
| POST /api/coordinator/students/{studentId}/contact-logs | C3_contact_log_create.json |
| GET /api/coordinator/applications/{applicationId} | C4_coordinator_application.json |
| POST /api/coordinator/applications/{applicationId}/approve | C4_approve.json |
| POST /api/coordinator/applications/{applicationId}/reject | C4_reject.json |
| POST /api/coordinator/applications/{applicationId}/deliver | C4_deliver.json |
| 잘못된 요청 / 대상 없음 / 상태 오류 | ERROR_example.json의 error 구조 |

## 초기 상태와 데이터 일관성

- 기본 HTTP 서버는 `db/seed.sql`을 읽어 메모리에 적재한다. DB 연결이나 SQL 실행은 없다.
  현재 원본 시드는 로컬 `dev`의 시드와 동일하다. SQL/공용 목업 파일은 수정하지 않는다.
- 수료생 50명(id 1~50), 교육 과정 2개, 공고 15건, 추천 묶음 31개,
  추천 항목 93개, 도움 요청 4건, 지원서/서류 4건, 상담 기록 3건을 반영한다.
  등록 학력/자격증/프로젝트/자기소개서/역량 프로필도 원본 그대로 연결한다.
- 공고 501~503은 활성 개발용 공고다. 601~612는 마감된 이력용 공고이며 새 추천에서 제외한다.
  실제 공고 수집 및 AI 매칭은 수행하지 않는다.
- 지원서 101/102는 SUBMITTED, 103은 APPROVED, 104는 DELIVERED를 유지한다.
  학생 1에게 목업 전용 지원서 7이나 응답 이력을 추가하지 않는다.
- 날짜 함수는 서버 시작 시 서울 날짜를 기준으로 해석한다. 상태는 응답/도움 요청/지원서로 계산한다.
  원본의 상대 날짜, ID, 근거 참조와 서류 본문을 유지한다.
- 새 ID는 원본 최대 ID 다음부터 발급한다. 지원서 생성은 105부터 시작한다.
- 원본에 없는 상세 필드는 `null`, 빈 목록은 `[]`로 반환한다.
  시드 공고의 출처는 원본 `DEV_SEED`이며 표시 문구는 "개발용 모의 공고"다.
- DB 내부 필드를 API에 노출하지 않고 `docs/API.md`와 기존 목업의 응답 필드로 변환한다.
  공고 상세의 축약 근거에는 `label`, `quote`, `url`, `collectedAt`을 `null`로 채운다.

학생 1에서 **현재 추천 → 지원하기 → 생성 완료 → 제출**로 새 지원서를 검증한다.
코디네이터의 기존 검토 대기에는 지원서 101/102가 표시된다.

### 빈 추천 화면 검증

같은 수료생으로 모든 후보 공고에 응답한 뒤 새로 추천받으면 `items: []`를 반환한다.
활성 후보는 총 3개이며 응답한 공고를 제외하므로, 남은 공고를 패스하고 새로 추천받는 과정을 반복하면 된다.
예를 들어 `/student?studentId=12`로 테스트하면 학생 1의 추천·지원서는 유지된다.
빈 상태에서는 새 제목·설명·프로필 안내와 새로 추천받기 버튼이 표시된다.
원래 공고가 있는 상태로 돌아가려면 JSON 서버를 재시작한다.

## 변경 및 모의 처리

- 새 지원서는 GENERATING으로 시작해 약 2.2초 뒤 DRAFT가 된다.
  시간 경과는 진행/서류/지원 목록 조회에서 같은 규칙으로 반영한다.
- 새 서류는 그 학생의 등록 자료로 만든다. 다른 기업의 인재상 문장이나 가짜 AI 근거를 복사하지 않는다.
  기업 정보는 NOT_FOUND이며 실제 AI 호출은 없다.
- 기존 지원서 101~104의 문서는 시드 원본을 사용하고 C4 검증 수치는 문서에서 다시 계산한다.
- 문장 수정은 source=USER, evidence=[]로 저장한다. 제출/승인/반려/전달은 허용된 상태에서만 처리한다.
  반려 note는 모의 상담 기록에도 남긴다. 실제 연락/전송은 하지 않는다.
- 문서와 프로필은 기본적으로 분리된다. saveToProfile=true일 때만 같이 저장한다.
  프로젝트의 공고별 문장(bullets)을 프로필로 복사하지 않는다.
- M8 수정 후 competencyProfile은 등록 프로젝트/기술/자격증을 요약하는 결정적인 대체 결과다.
  실제 역량 추출이나 추천 품질을 검증하는 서버가 아니다.
  근거는 존재하는 PROJECT ID만 사용하며 CAREER/COVER_LETTER 근거 규칙을 새로 정하지 않는다.
- 프로필 자료가 없는 학생은 빈 목록/nullable 필드로 반환한다.
- 프로필/문서 항목은 docs/API.md와 FE 타입의 필드를 사용한다.
  BASIC/SKILLS/MILITARY는 단일 값으로 수정만 지원한다.
  SKILLS item은 `{tags:[...]}`, MILITARY item은 `{value:...}` 형태다.
  목록 항목은 EDUCATION/CAREER/CERTIFICATES/PROJECTS, 포트폴리오는 기존 프로젝트의 포함 여부를 수정한다.
  TRAINING은 읽기 전용이며 자기소개서 원본은 PUT cover-letter, 서류 문장은 UPDATE_SENTENCE를 사용한다.
- FE ProfileProject에 없는 readme/roleNote/source 원문 필드는 첨부 AI 요청 계약과 구분한다.
  AI 요청 스키마의 nullable techStack을 FE 응답의 techStack 배열 형식으로 바꾸는 API를 새로 만들지 않는다.
- API 명세의 PROJECTS 설명에는 techStack이 있으나 FE 서류 ProjectItem에는 없다.
  목업은 techStack 입력을 saveToProfile에만 반영하고 서류 응답에 새 키를 추가하지 않는다.
- 실패한 변경은 원복한다. CORS는 localhost:3000 / 127.0.0.1:3000만 허용한다.
- 실패/지연 AI 시뮬레이터, 공고 수집 배치, 실제 Gemini 호출, DB·파일 영속화는 구현하지 않는다.

## 자동 검증

```powershell
node --test frontend/src/components/student/dev-mock/server.test.mjs frontend/src/components/student/dev-mock/seed.test.mjs
```

시드 테스트는 전체 적재·관계·상태·날짜와 HTTP 응답을 검증한다. 기존 흐름 테스트는
`dataSource: "fixtures"`로 공용 목업 기반 회귀 시나리오를 검증한다.
테스트는 임의의 로컬 포트와 독립 메모리를 사용해 실행 중인 4000 서버를 바꾸지 않는다.
생성 시간은 테스트 안에서만 제어한다. 공개 API에 테스트 전용 경로나 JSON 키를 추가하지 않는다.

서버를 Ctrl+C로 종료 후 다시 실행하면 변경 내용이 초기화된다.
실제 BE로 전환할 때는 목업 서버를 종료하고 기존 FE 변수의 주소만 바꿔 FE를 재시작한다.
실제 BE와의 연결 및 AI 품질 검증은 별도로 수행해야 한다.
