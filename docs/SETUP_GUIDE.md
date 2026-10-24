# 첫 셋팅 가이드 (PM용)

주말에 PM이 한 번만 합니다. 순서대로 하면 됩니다: **① 묶음 풀기 → ② Claude Code로 뼈대 만들기 → ③ 실행 확인 → ④ 첫 push → ⑤ GitHub 설정 → ⑥ 팀 공유**

---

## ① 묶음 풀기

```bash
git clone https://github.com/<계정>/<레포>.git ieum
cd ieum
# 받은 zip의 내용물(AGENTS.md, docs/, db/ 등)을 이 폴더 바로 아래에 복사
```

복사 후 폴더 바로 아래에 `AGENTS.md`, `CLAUDE.md`, `README.md`, `.gitignore`, `.github/`, `docs/`, `db/`, `mock/`, `frontend/`, `backend/`, `ai/`가 보이면 됩니다. (`.gitignore`, `.github`은 숨김 파일이라 탐색기에서 안 보일 수 있습니다.)

VS Code로 `ieum` 폴더를 열고, 터미널에서 `claude`를 실행합니다.

---

## ② Claude Code로 뼈대 만들기

아래 블록 전체를 Claude Code에 그대로 붙여 넣습니다. 중간에 명령 실행 허락을 물으면 내용을 보고 허락합니다.

````text
이 저장소는 해커톤 프로젝트 "이음"이다. 먼저 AGENTS.md, README.md, docs/API.md, docs/REQUIREMENTS.md, db/schema.sql을 읽어라.
나는 PM이고, 지금은 팀원 6명이 월요일부터 바로 병렬 개발할 수 있게 세 프로젝트의 "뼈대"만 만든다.
기능 코드는 만들지 않는다. 아래에 적은 것만 만들고, 파일마다 주인 파트를 주석으로 첫 줄에 적는다 (AGENTS.md "폴더 주인" 표 기준).
기술 스택과 버전은 AGENTS.md에 적힌 것만 쓴다. 라이브러리는 아래에 적힌 것만 추가한다.

## 1. frontend/ (Next.js)
- 이미 frontend/.env.example 이 있으므로, 임시 폴더에 create-next-app으로 만든 뒤 내용을 frontend/로 옮겨라.
  옵션: TypeScript, ESLint, Tailwind CSS, App Router, src/ 디렉터리, import alias @/*, npm. Turbopack 질문은 기본값.
- 루트의 mock/ 폴더를 frontend/public/mock/ 으로 git mv 하고 루트 mock/ 은 지운다.
- 디자인 토큰 (주인 FE-A): Tailwind 테마에 아래 색을 이름으로 등록하고, 글꼴은 Noto Sans KR(next/font/google).
  primary #047857, primary-strong #065F46, primary-soft #D1FAE5, primary-tint #ECFDF5,
  ink #1F2937, ink-sub #5B6472, ink-muted #6B7280, line #E5E0D8, surface #FFFFFF, surface-warm #FEF7EE,
  page #E7EAE8, warn-bg #FEF3C7, warn-ink #78350F, danger #B91C1C, danger-bg #FEE2E2, coord-bg #F1F5F9.
  Tailwind 버전에 맞는 방식(v4면 globals.css의 @theme, v3면 tailwind.config)으로 넣고, 토큰은 src/styles/ 한 곳에만 둔다.
- 라우트(빈 페이지: 화면 번호와 화면 이름을 h1으로만 표시, 주인 주석 포함):
  /                                   R0 역할 선택 (버튼 2개: /student, /coordinator)  FE-A
  /student                            M1 맞춤 공고 홈                                   FE-A
  /student/jobs/[itemId]              M2 공고 상세 · 응답                               FE-A
  /student/jobs/[itemId]/pass         M3 패스 사유                                      FE-A
  /student/help/[helpRequestId]       M4 도움 요청 완료                                 FE-A
  /student/applications/[applicationId]/progress     M5 서류 생성 중                   FE-A
  /student/applications/[applicationId]              M6 이력서 초안                     FE-B
  /student/applications/[applicationId]/cover-letter M6-1 자기소개서 편집             FE-B
  /student/applications               M7 지원 현황                                      FE-A
  /student/profile                    M8 내 프로필                                      FE-B
  /coordinator                        C1 대시보드                                       FE-B
  /coordinator/students               C2 수료생 목록                                    FE-A
  /coordinator/students/[studentId]   C3 수료생 상세                                    FE-A
  /coordinator/applications/[applicationId]          C4 서류 검토 · 승인               FE-B
  M6-2는 라우트가 아니라 바텀시트 컴포넌트이므로 페이지를 만들지 않는다.
- 레이아웃:
  app/student/layout.tsx (FE-A): 폭 390px 고정 가운데 정렬, 하단 메뉴 3탭(맞춤 공고 /student · 지원 현황 /student/applications · 내 프로필 /student/profile) 자리만.
  app/coordinator/layout.tsx (FE-B): 데스크톱 고정, 왼쪽 사이드바(대시보드 · 수료생 목록 · 서류 검토) 자리만.
- 빈 폴더(.gitkeep): src/components/ui, src/components/student, src/components/resume, src/components/coordinator
- API 호출 (src/lib/api/):
  client.ts (FE-A): apiGet/apiPost/apiPatch/apiPut/apiDelete. NEXT_PUBLIC_USE_MOCK=true면 경로에 맞는 /mock/파일명.json 을 fetch,
    false면 NEXT_PUBLIC_API_BASE_URL + 경로 호출. 응답의 data를 꺼내 돌려주고, error면 code·message를 가진 ApiError를 던진다.
    목업 파일 매핑은 각 도메인 파일에서 함수마다 mock 파일명을 넘기는 방식으로 한다.
  student.ts · coordinatorStudents.ts (FE-A), resume.ts · profile.ts · coordinatorReview.ts (FE-B):
    docs/API.md의 엔드포인트마다 함수 시그니처만 만들고 본문은 client 호출 한 줄. 화면이 아직 안 써도 된다.
- 타입 (src/types/): student.ts (FE-A), resume.ts · profile.ts · coordinator.ts (FE-B).
  docs/API.md 1장의 공통 코드 값(response, passReason, 지원서 status, 수료생 status, subStatus, source 등)을 string union으로,
  public/mock/ JSON 모양대로 응답 타입을 만든다. 공통 코드 값은 student.ts에 두고 나머지가 import한다.

## 2. backend/ (Spring Boot)
- Spring Initializr로 생성: Gradle - Groovy, Java 17, Spring Boot 3 계열 최신 안정판(4.x 금지), group com.ieum, artifact backend, package com.ieum.
  의존성: Spring Web, Validation, MyBatis Framework, MySQL Driver, Lombok, + springdoc-openapi-starter-webmvc-ui (Boot 3 호환 버전).
  Spring Security는 넣지 않는다 (P0 로그인 없음). JPA 넣지 않는다.
- application.yml (BE-A):
  spring.config.import: optional:file:.env[.properties]
  datasource는 ${DB_URL} ${DB_USERNAME} ${DB_PASSWORD}, server.port 8080,
  jackson: 시간대 Asia/Seoul, 날짜를 timestamp가 아닌 문자열로, null 값도 출력,
  mybatis: mapper-locations classpath:mapper/**/*.xml, map-underscore-to-camel-case true,
  app.ai.server-url ${AI_SERVER_URL}, app.ai.mock ${AI_MOCK:true}, app.seoul.openapi-key ${SEOUL_OPENAPI_KEY:}, app.seoul.job-service recMntList, app.seoul.it-job-codes 빈 목록, app.frontend-origin ${FRONTEND_ORIGIN:http://localhost:3000}
- com.ieum.common (BE-A):
  ApiResponse<T> { data } 와 정적 팩토리, ErrorResponse { error: { code, message } },
  ErrorCode enum (INVALID_REQUEST 400, PASS_REASON_REQUIRED 400, STUDENT_NOT_FOUND 404, APPLICATION_NOT_FOUND 404, RECOMMENDATION_ITEM_NOT_FOUND 404, ALREADY_RESPONDED 409, INVALID_STATUS 409, INTERNAL_ERROR 500, AI_SERVER_ERROR 500) — 메시지는 화면에 그대로 띄울 수 있는 한국어,
  BusinessException(ErrorCode), GlobalExceptionHandler(BusinessException, 검증 실패, 그 밖의 예외 → ErrorResponse),
  WebConfig: CORS로 app.frontend-origin 만 허용 (/api/**, GET POST PATCH PUT DELETE),
  OpenApiConfig: 제목 "이음 API", HealthController: GET /api/health → { "data": { "status": "OK" } }.
- com.ieum.common.mybatis (BE-B): JSON 컬럼용 TypeHandler 하나 (Jackson으로 Object/JsonNode 변환).
- com.ieum.ai (BE-A): AiClient 인터페이스 + RestClient로 호출하는 구현 + app.ai.mock=true면 고정 응답을 돌려주는 구현. 메서드는 docs/API.md 4장의 AI 엔드포인트 6개. 본문은 TODO.
- 빈 패키지는 package-info.java에 주인 · 담당 화면 · 요구사항 ID를 주석으로:
  job (BE-A, docs/JOB_API.md 참고) · recommendation · application (BE-A), profile · helprequest · status · coordinator · domain (BE-B).
  src/main/resources/mapper/ 아래에도 같은 이름 폴더와 .gitkeep.
- 테스트: contextLoads 테스트는 DB 없이도 통과하도록 지운다 (P0는 수동 확인).

## 3. ai/ (FastAPI)
- requirements.txt (PM): fastapi, uvicorn[standard], pydantic, pydantic-settings, python-dotenv, httpx, google-genai.
  임베딩 · 리랭커 라이브러리는 AI-A가 월요일에 모델을 정한 뒤 추가하므로 넣지 않는다. 버전은 지금 설치되는 버전으로 == 고정.
- common/config.py (AI-A): pydantic-settings로 .env의 GEMINI_API_KEY, LLM_MODEL, EMBEDDING_MODEL, RERANKER_MODEL 읽기.
  common/llm.py, common/embedding.py, common/reranker.py: 함수 시그니처와 TODO만. common/response.py: ok(data) → {"data": ...}, error(code, message).
- schemas/ (AI-A): docs/API.md 3장의 역량 프로필 · 근거 · 서류 JSON을 pydantic 모델로 (camelCase alias, populate_by_name).
- 라우터 (각 폴더의 router.py, APIRouter, 응답 본문은 501 + {"error": {"code": "NOT_IMPLEMENTED", "message": "아직 구현 전이에요."}}):
  profile/router.py   POST /ai/profile/extract       AI-B
  matching/router.py  POST /ai/matching/index, POST /ai/matching/recommend   AI-B
  company/router.py   POST /ai/company/collect       AI-B
  document/router.py  POST /ai/document/draft        AI-A
- main.py (AI-A): FastAPI(title="이음 AI"), 위 4개 라우터 include, GET /ai/health → {"data": {"status": "OK"}}.
- 빈 폴더(.gitkeep): ai/eval (AI-A), ai/data (AI-B). 각 폴더에 __init__.py.

## 4. 마무리
- .vscode/extensions.json에 추천 확장(Java Extension Pack, Spring Boot Extension Pack, Python, ESLint, Tailwind CSS IntelliSense)을 넣는다.
- 세 서버를 각각 실행해 확인하고 결과를 알려줘:
  (1) cd frontend && npm install && npm run build
  (2) cd backend && ./gradlew build -x test   (DB 없이 빌드만)
  (3) cd ai && python -m venv .venv && pip install -r requirements.txt && python -c "import main"
- README.md의 실행 방법과 실제로 만든 것이 다르면 README를 맞춰 고친다.
- 커밋은 하지 마라. 내가 확인한 뒤 직접 커밋한다. git push --force, git reset --hard는 쓰지 않는다.
````

---

## ③ 실행 확인

Claude Code가 끝나면 README의 "실행 방법"대로 직접 한 번씩 띄워 봅니다.

- [ ] `mysql -u root -p < db/schema.sql` → `mysql -u root -p ieum < db/seed.sql` 오류 없음
- [ ] BE: http://localhost:8080/api/health 와 `/swagger-ui.html` 열림
- [ ] AI: http://localhost:8000/ai/health 와 `/docs` 열림
- [ ] FE: http://localhost:3000 에서 R0 → 수료생 · 코디네이터 화면 이동, 하단 메뉴 · 사이드바 보임
- [ ] `git status`에 `.env`, `node_modules`, `build`, `.venv`가 **안** 보임

---

## ④ 첫 push (보호 규칙 켜기 전)

```bash
git add .
git status            # .env가 없는지 한 번 더 확인
git commit -m "chore: 프로젝트 초기 셋팅 (문서 · DB · FE/BE/AI 뼈대)"
git push origin main
```

---

## ⑤ GitHub 설정

웹에서 해도 되고, `gh` CLI가 로그인되어 있으면 Claude Code에 아래 명령 실행을 맡겨도 됩니다. `<계정>/<레포>`를 바꿔서 씁니다.

### 병합 방식 (Settings → General → Pull Requests)

- Allow merge commits **켬** · Allow squash merging **끔** · Allow rebase merging **끔**
- Always suggest updating pull request branches **끔** (Update branch 버튼 숨김)
- Automatically delete head branches **끔** (1인 1브랜치를 계속 씀)

```bash
gh repo edit <계정>/<레포> --enable-merge-commit=true --enable-squash-merge=false \
  --enable-rebase-merge=false --delete-branch-on-merge=false --allow-update-branch=false
```

### main 보호 (Settings → Branches → Add branch protection rule → `main`)

- Require a pull request before merging **켬** · Required approvals **0**
- Allow force pushes **끔** · Allow deletions **끔**
- Do not allow bypassing the above settings **켬** (PM도 PR로만 넣기)

```bash
gh api -X PUT repos/<계정>/<레포>/branches/main/protection --input - <<'EOF'
{
  "required_status_checks": null,
  "enforce_admins": true,
  "required_pull_request_reviews": { "required_approving_review_count": 0 },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF
```

> **비공개(private) 저장소 + 무료 계정이면** 브랜치 보호가 켜지지 않습니다. 저장소를 공개로 두거나(키는 어차피 올리지 않음), 보호 없이 규칙(GIT_RULES)만으로 운영합니다.

### 팀원 초대 (Settings → Collaborators → Add people, 권한 Write)

```bash
gh api -X PUT repos/<계정>/<레포>/collaborators/<깃허브아이디> -f permission=push
```

- [ ] 김하윤 (FE-A) · [ ] 유지수 (FE-B) · [ ] 유능한 (BE-B) · [ ] 심소민 (AI-A) · [ ] 조영동 (AI-B)

### 확인

- [ ] PR 화면 병합 버튼이 "Create a merge commit" 하나만 보임
- [ ] `main`에 직접 push하면 거절됨

---

## ⑥ 팀 공유 (디스코드)

- 저장소 주소 + "README 실행 방법대로 띄우고, 처음 한 번 GIT_RULES의 '처음 한 번' 명령으로 내 브랜치 만들기"
- **비공개 채널**에 키 2개: `SEOUL_OPENAPI_KEY`(BE용), `GEMINI_API_KEY`(AI용)

---

## 실제 셋팅 메모 (2026-10-24, PM)

- Spring Initializr가 Boot 4.x만 제공해 래퍼만 받고 `build.gradle`은 직접 작성: Boot 3.5.16 · MyBatis starter 3.0.5 · springdoc 2.8.17 · Gradle 8.14.3.
- JDK는 17·21 둘 다 됨 (`options.release = 17`로 바이트코드 17 고정). 셋팅 PC에는 21만 있었음.
- create-next-app 결과는 Next 16 · Tailwind v4 → 디자인 토큰은 `src/styles/tokens.css`의 `@theme` 블록(tailwind.config 없음).
- `next.config.ts`의 `cacheComponents`는 껐음 (동적 라우트가 params를 바로 읽기 위해). 생성된 `frontend/.gitignore`는 `.env.example`까지 막아 삭제(루트 .gitignore가 덮음).
- AI 고정 응답은 `backend/src/main/resources/ai-mock/*.json`, 목업이 없던 API 3개는 `public/mock/`에 C1_help_request_update · C3_contact_log_create · C4_reject 추가.
