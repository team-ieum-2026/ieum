# 이음 P0 요구사항 명세서

P0 개발 기간(10/26~11/1) 동안 각자 자기 파트 목록을 체크하며 씁니다. 다 끝나면 PR 설명에 완료한 요구사항 ID를 적습니다. 예: `완료: FE-A-03, FE-A-04`

- 화면 번호: 와이어프레임 (`docs/wireframe/`) · 흐름 번호: 흐름도 (`docs/flow/`)
- API · JSON 형식: `docs/API.md` · 테이블: `db/schema.sql`, `docs/ERD.md`
- 우선순위 표시가 없는 항목은 모두 P0입니다.

---

## 0. 모두 지킬 것

- [ ] **ALL-01** 수료생 화면은 폭 390px 고정, 코디네이터 화면은 데스크톱 고정. 반응형은 만들지 않는다.
- [ ] **ALL-02** 수료생 화면 문구에 "취업 여부", "보고"를 묻는 표현을 쓰지 않는다.
- [ ] **ALL-03** 색 · 글꼴은 디자인 토큰만 쓴다. 화면 코드에 색값을 직접 쓰지 않는다.
- [ ] **ALL-04** API 요청 · 응답은 `docs/API.md` 1장의 공통 JSON 규칙을 따른다.
- [ ] **ALL-05** 모든 이름 · 수치는 모의 데이터. 실명 · 연락처 · 성적은 넣지 않는다.

---

## 1. 화면별 찾아보기

| 화면 | 이름 | FE | BE | AI |
| --- | --- | --- | --- | --- |
| R0 | 역할 선택 | FE-A-02 | BE-A-01 | - |
| M1 | 맞춤 공고 홈 | FE-A-03 ~ 05 | BE-A-04 | AI-B-03 |
| M2 | 공고 상세 · 응답 | FE-A-06 ~ 08 | BE-A-05, BE-A-06, BE-B-05 | AI-B-03 |
| M3 | 패스 사유 | FE-A-09 | BE-A-06 | - |
| M4 | 도움 요청 완료 | FE-A-10 | BE-B-05 | - |
| M5 | 서류 생성 중 | FE-A-11 | BE-A-07 | AI-A-03, AI-B-04 |
| M6 | 이력서 초안 | FE-B-01 ~ 03 | BE-A-08 | AI-A-03 |
| M6-1 | 자기소개서 편집 | FE-B-01, FE-B-04 | BE-A-08 | AI-A-04, AI-A-05 |
| M6-2 | 섹션 편집 시트 | FE-B-05 | BE-A-08, BE-B-04 | - |
| M7 | 지원 현황 | FE-A-12 | BE-A-09 | - |
| M8 | 내 프로필 | FE-B-06 | BE-B-03, BE-B-04 | AI-B-01 |
| C1 | 대시보드 | FE-B-07 | BE-B-05 ~ 07 | - |
| C2 | 수료생 목록 | FE-A-14 | BE-B-08 | - |
| C3 | 수료생 상세 | FE-A-15 | BE-B-08 | AI-B-01 |
| C4 | 서류 검토 · 승인 | FE-B-01, FE-B-08 | BE-B-09 | AI-A-04 |

---

## 2. FE-A · 김하윤 — 수료생 기본 화면 + 단순 목록

- [x] **FE-A-01** 공통 부품: 버튼(주 · 보조 · 위험), 태그(AI 작성 · 등록 자료 · 직접 입력), 카드, 바텀시트, 상태 칩, 하단 메뉴. 디자인 토큰 파일 관리.
  - 완료 기준: FE-B가 import해서 바로 쓸 수 있음. 색은 토큰 파일 한 곳에만 있음.
- [x] **FE-A-02** R0: "수료생으로 보기" → M1, "코디네이터로 보기" → C1. 수료생 화면은 `?studentId=` 값(없으면 `NEXT_PUBLIC_DEMO_STUDENT_ID`)으로 수료생을 정하고, 화면 이동 중에도 유지.
- [x] **FE-A-03** M1: 추천 카드 3장. 카드마다 회사, 직무, 지역 · 고용 형태 · 경력, 마감 D-n, 추천 근거 2줄.
  - API: `GET /api/students/{id}/recommendations/current`
  - 완료 기준: 카드를 누르면 M2로 이동. 마감 7일 이내는 강조 색. `untilFilled`면 "채용 시 마감"을 함께 표시.
- [x] **FE-A-04** M1: "새로 추천받기" 버튼과 추천 시각 · 기준 표시. 누르면 로딩 후 카드 갱신.
  - API: `POST /api/students/{id}/recommendations/refresh`
- [x] **FE-A-05** M1: 이미 응답한 카드에 상태 표시(지원함 · 패스).
- [x] **FE-A-06** M2: 공고 상세(근무지 · 고용 형태 · 마감), 추천 이유(근거 출처 포함), 공고 요약, 데이터 출처 표기(`sourceLabel`, 원문 링크 없음).
  - API: `GET /api/recommendation-items/{itemId}`
- [x] **FE-A-07** M2: "지원하기" → 지원 응답 → 받은 `applicationId`로 M5 이동.
  - API: `POST /api/recommendation-items/{itemId}/response` `{ "response": "APPLY" }`
- [x] **FE-A-08** M2: "도움 요청" → 도움 요청 생성 → M4 이동.
  - API: `POST /api/help-requests`
- [x] **FE-A-09** M3: 패스 사유 6개 라디오. "기타"를 고를 때만 입력칸 활성. "패스하기" → M1.
  - API: `POST /api/recommendation-items/{itemId}/response` `{ "response": "PASS", ... }`
  - 완료 기준: 사유를 안 고르면 버튼 비활성.
- [x] **FE-A-10** M4: 연락 방법(문자 · 전화 · 이메일, 선택)과 메모(선택). "확인" → M1.
  - API: `PATCH /api/help-requests/{id}`
- [x] **FE-A-11** M5: 진행 단계 3개(기업 정보 확인 · 자기소개서 · 이력서)를 2초마다 조회해 표시. `DRAFT`면 M6 이동, `FAILED`면 다시 시도 버튼.
  - API: `GET /api/applications/{id}/progress`
- [x] **FE-A-12** M7: 지원서별 단계(서류 제출 → 담당 코디네이터 검토 → 기관 추천 전달)와 이번 추천의 다른 공고 응답 상태.
  - API: `GET /api/students/{id}/applications`
- [x] **FE-A-13** 하단 메뉴 3탭: 맞춤 공고(M1) · 지원 현황(M7) · 내 프로필(M8).
- [x] **FE-A-14** C2: 상태 필터(전체 · 취업 완료 · 구직 중 · 도움 필요 · 추가 확인 필요), 이름 검색, 표(이름 · 과정 · 수료일 · 상태 · 세부 · 마지막 응답 · 비고 여부), 상세 링크.
  - API: `GET /api/coordinator/students`
- [x] **FE-A-15** C3: 기본 정보, 역량 프로필, 등록 자료, 추천 · 응답 이력, 비고 · 상담 기록, "통화 · 상담 기록 추가".
  - API: `GET /api/coordinator/students/{id}`, `POST /api/coordinator/students/{id}/contact-logs`

---

## 3. FE-B · 유지수 — 서류 · 프로필 · 대시보드

- [x] **FE-B-01** 이력서 문서 부품: 섹션별 표시, 출처 태그, AI 문장 강조, 문장 선택 시 근거 표시. M6-1과 C4에서 같이 쓴다.
- [x] **FE-B-02** M6: 섹션 카드 9개(기본정보 · 학력 · 교육 · 경력 · 병역 · 자격증 · 기술 · 경험 · 포트폴리오) + 자기소개서 미리보기 카드. 카드마다 요약 한 줄과 출처 태그. "자동 저장됨" 표시.
  - API: `GET /api/applications/{id}`
  - 완료 기준: 병역 카드는 비어 있어도 "선택 입력"으로 표시되고 제출이 막히지 않음.
- [x] **FE-B-03** M6: "담당자에게 검토 요청" → M7 이동.
  - API: `POST /api/applications/{id}/submit`
- [x] **FE-B-04** M6-1: 자기소개서 전체 화면. 문장을 누르면 근거(출처 · 원문 · 수집일) 표시, 직접 수정 → 해당 문장은 "직접 입력"으로 표시. `companyInfoStatus`가 `PARTIAL` · `NOT_FOUND`면 "공고 외 정보 확인 불가" 안내.
  - API: `PATCH /api/applications/{id}/document` `UPDATE_SENTENCE`
- [x] **FE-B-05** M6-2: 섹션 편집 바텀시트. 자격증으로 먼저 만들고 학력 · 병역 · 경력 등 같은 틀로 확장. "내 프로필에도 저장" 체크박스(기본 선택).
  - API: `PATCH /api/applications/{id}/document` `ADD_ITEM` · `UPDATE_ITEM` · `DELETE_ITEM`
  - 완료 기준: M8에서 열면 체크박스를 숨기고 저장 뒤 M8로 돌아감. M6에서 열면 M6로 돌아감.
- [ ] **FE-B-06** M8: 역량 프로필 카드(역할 · 기술 태그), 등록 자료 목록, 항목 수정 후 "반영 완료" 배너.
  - API: `GET /api/students/{id}/profile`, 프로필 항목 · 자기소개서 수정 API
- [ ] **FE-B-07** C1: 도움 요청 영역(맨 위), 상태별 집계 4개 + 구직 중 세부 3개, 전체 비율 막대, 할 일 3종(서류 검토 대기 · 전화 확인 필요 · 비고 확인).
  - API: `GET /api/coordinator/dashboard`
  - 완료 기준: "추가 확인 필요(무응답)"와 "구직 중(미취업)"이 다른 색 · 다른 칸으로 구분됨.
- [ ] **FE-B-08** C4: 서류 전체 + 선택 문장 근거 + 검증 요약(AI 문장 수 · 근거 연결 수 · 제외 문장 수 · 직접 수정 수 · 기업 정보 상태), 버튼 3개(반려 · 승인 · 전달 완료). 전달 완료는 승인 뒤에만 활성.
  - API: `GET /api/coordinator/applications/{id}`, `approve` · `reject` · `deliver`
- [ ] **FE-B-09** 코디네이터 공용 레이아웃 · 사이드바(대시보드 · 수료생 목록 · 서류 검토 + 대기 건수).

---

## 4. BE-A · 김용현 — 수료생 흐름

- [x] **BE-A-01** 공통: 응답 · 에러 형식, CORS(FE 주소 허용), Swagger 문서. 수료생 API는 모두 경로의 `studentId`로 동작(특정 수료생 고정 금지).
- [x] **BE-A-02** 공고 일 배치(S3): 서울 열린데이터광장 `recMntList`를 1,000건씩 받아 IT 직종만 `job_posting`에 upsert. 매핑 · 필터 · 해시 ID 규칙은 `docs/JOB_API.md`. 마감 · 사라진 공고는 `is_active = 0`. 수동 실행 `POST /api/admin/jobs/sync`.
  - 완료 기준: 첫 배치 후 IT 직종 코드 목록과 남은 건수를 디스코드에 공유.
- [x] **BE-A-03** 배치 뒤 AI 색인 갱신 호출(`content_hash`가 바뀐 공고만).
  - API: AI `POST /ai/matching/index`
- [x] **BE-A-04** 현재 추천 조회(없으면 AI 매칭 호출 → `recommendation`, `recommendation_item` 저장)와 새로 추천받기(응답한 공고 제외).
- [x] **BE-A-05** 추천 공고 상세 조회.
- [x] **BE-A-06** 지원 · 패스 응답 저장. `PASS`면 사유 필수. 응답 시 `last_response_at` 갱신, BE-B 상태 재계산 호출.
  - 완료 기준: 이미 응답한 항목에 다시 응답하면 409.
- [x] **BE-A-07** 지원 시 `application` 생성(`GENERATING`) → AI 서류 생성 비동기 호출 → `application_document` 저장 → `DRAFT`. 실패 시 `FAILED`. 진행 상태 API.
- [x] **BE-A-08** 서류 조회 · 수정 · 제출. 수정된 항목은 `source = USER`. `saveToProfile`이면 BE-B 프로필 서비스 호출. 제출 시 `SUBMITTED` + `last_response_at` 갱신.
- [x] **BE-A-09** 지원 현황(M7) 조회.
- [x] **BE-A-10** 시연 기업 정보 수집 결과 저장(AI `POST /ai/company/collect` 호출 → `company_info`).

---

## 5. BE-B · 유능한 — 코디네이터 흐름 + 데이터

- [ ] **BE-B-01** `db/schema.sql` 관리. 바뀌면 `docs/ERD.md` · `docs/DATA_SPEC.md`도 갱신하고 디스코드에 공지. MyBatis 매퍼와 JSON 컬럼 TypeHandler.
- [ ] **BE-B-02** `db/seed.sql`(PM 작성, 수료생 50명)을 적재해 상태 판정 결과가 시드의 `student_status`와 같은지 확인. 스키마가 바뀌면 시드도 함께 고침.
- [x] **BE-B-03** 프로필 조회 · 항목 추가 · 수정 · 삭제 · 자기소개서 수정. 바뀌면 AI 재추출 호출 → `competency_profile` 저장.
  - API: AI `POST /ai/profile/extract`
- [x] **BE-B-04** BE-A가 부를 프로필 저장 서비스 메서드 제공(`saveToProfile`).
- [ ] **BE-B-05** 도움 요청 생성 · 수정(수료생), 처리 상태 변경(코디네이터).
- [ ] **BE-B-06** 상태 판정 규칙(S12): 응답 · 도움 요청 · 제출 · 승인이 생길 때 재계산 + 하루 1회 1주 무응답 판정. 규칙은 `db/schema.sql` 맨 아래.
  - 완료 기준: 시드 50명의 상태가 대시보드 숫자와 일치.
- [ ] **BE-B-07** 대시보드 API: 도움 요청(열린 것), 상태별 집계, 서류 검토 대기, 전화 확인 필요(마지막 응답 오래된 순), 비고 확인.
- [ ] **BE-B-08** 수료생 목록(필터 · 검색 · 페이지) · 상세 · 통화 기록 추가.
- [ ] **BE-B-09** 코디네이터 서류 조회(검증 요약 계산 포함) · 승인 · 반려 · 전달 완료. 상태 전이가 맞지 않으면 409.

---

## 6. AI-A · 심소민 (AI 리드) — 서류 흐름 + 공용 기반

- [x] **AI-A-01** `ai/common/`: LLM 호출 모듈(모델 · 키 설정 한 곳), 임베딩 · 리랭커 모듈. AI-B가 import해서 씀.
- [x] **AI-A-02** 데이터 형식 관리: 역량 프로필 · 근거 · 서류 JSON, 기업 정보는 초안(`docs/API.md` 3장, `ai/schemas/`) 그대로 시작. 바꿀 땐 디스코드에 알리고 두 곳을 함께 고침.
- [ ] **AI-A-03** `POST /ai/document/draft`: 등록 자기소개서를 공고 인재상 · 기업 정보에 맞춰 다시 쓰고, 이력서 섹션(기술 요약 · 프로젝트 bullet)을 공고 기준으로 정리.
- [ ] **AI-A-04** 문장별 근거 연결과 검증: `source = AI` 문장은 근거 1개 이상, 근거 없는 문장은 제외하고 `excludedSentenceCount`에 기록.
  - 완료 기준: 생성 서류 20건에서 근거 일치율 측정 가능.
- [ ] **AI-A-05** 기업 정보가 없거나 일부면 지어내지 않고 `companyInfoStatus`를 `PARTIAL` · `NOT_FOUND`로 표시.
- [ ] **AI-A-06** `GET /ai/health`.
- [ ] **AI-A-07** 평가 틀: 매칭 Top-3 적합률, 근거 일치율, 상태 분류 정확도를 집계하는 스크립트. 채점은 FE-A · FE-B가 독립 평가자로 함.
- [ ] **AI-A-08** 11/1까지 `requirements.txt` 버전 고정과 실행 방법을 README에 정리해 PM에게 공유.

---

## 7. AI-B · 조영동 — 추천 흐름

- [x] **AI-B-01** `POST /ai/profile/extract`: 등록 자료에서 역할 · 기술 · 성과 추출, 항목마다 근거 연결.
- [x] **AI-B-02** `POST /ai/matching/index`: 공고 임베딩 색인. `contentHash`가 바뀐 공고만 다시 계산.
- [ ] **AI-B-03** `POST /ai/matching/recommend`: 임베딩으로 후보를 뽑고 리랭커로 Top-3. 회사명 · 제목이 같은 공고(근무지만 다름)는 하나로 합침. 제외 목록 반영, 공고마다 추천 근거(카드용 2줄 이상)와 근거 출처.
- [ ] **AI-B-04** `POST /ai/company/collect`: 기업 홈페이지 인재상 · 채용 페이지와 최근 6개월 뉴스 1~2건에서 인재상 키워드 · 핵심 가치 · 최근 이슈 1개 추출. 출처 URL · 수집일 저장. 못 찾으면 `NOT_FOUND`.
- [ ] **AI-B-06** (P1) 위험 표현 감지: 도움 요청 메모 · 패스 기타 입력에서 위험 표현을 찾아 C1 도움 요청 칸에 표시. 통합 기간 여유가 있을 때만.

---

## 8. 통합 완료 기준 (11/2~11/4)

- [ ] **INT-01** 메인 흐름이 끝까지 동작: M1 → M2 → M5 → M6 → M6-1 · M6-2 → 담당자에게 검토 요청 → M7 → C1 서류 검토 대기 → C4 승인 → 전달 완료 → M7 단계 갱신.
- [ ] **INT-02** 도움 요청 흐름: M2 → M4 → C1 맨 위에 표시.
- [ ] **INT-03** 패스 흐름: M3 "이미 취업했어요" → C1 취업 완료 숫자 증가.
- [ ] **INT-04** 프로필 흐름: M8 자격증 추가 → 반영 완료 → M1 새로 추천받기에 반영.
- [ ] **INT-05** 정량 지표 3종 수치 확보: 매칭 Top-3 적합률(목표 70%), 서류 근거 일치율(목표 95%), 상태 분류 정확도(목표 85%).
- [ ] **INT-06** 시연 기업 3~5곳 정보 수집 완료, 그중 1곳은 "공고 외 정보 확인 불가" 시연용.
- [ ] **INT-07** 수료생별 추천 확인: 평가용 수료생(id 1~20)을 바꿔 가며 M1 Top-3가 사람마다 다르게, 희망 직무에 맞게 나옴. 맞는 공고가 없을 수료생(게임 · 임베디드 · 부산)도 엉뚱한 공고로 채우지 않음.
- [ ] **INT-08** 시연 준비: id 1을 주인공 실제 자료(팀원 결과물, 가명, 본인 동의)로 교체하고 메인 흐름 다시 확인.
