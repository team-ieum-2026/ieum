# 이음 P0 기술 스택

2026-10-24 기준. 버전은 실제 설치 · 고정된 값이며, 정확한 출처는 `frontend/package.json` · `backend/build.gradle` · `ai/requirements.txt`입니다.
스택 변경 · 라이브러리 추가는 AGENTS.md 규칙대로 PM 확인 후 `chore/` 브랜치 PR로만 합니다.

## 호출 방향

브라우저 → FE(3000) → BE(8080) → AI(8000) → (BE가) DB(3306)

- FE는 BE만 호출한다. AI 서버를 직접 부르지 않는다.
- AI 서버는 DB에 접근하지 않는다. BE가 입력을 주고 JSON을 받아 저장한다.

## FE — `frontend/`

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| Next.js (App Router) | 16.4.3 | 포트 3000, Turbopack |
| React | 19.3 | React Compiler 켜짐 |
| TypeScript | 5 | `strict` |
| Tailwind CSS | 4 | 디자인 토큰은 `src/styles/tokens.css`의 `@theme` 한 곳 |
| ESLint | 9 | `eslint-config-next` |
| Node.js / npm | 20 LTS 이상 / npm | yarn · pnpm 금지 |

- 목업: `NEXT_PUBLIC_USE_MOCK=true`(기본)면 `public/mock/*.json`을 읽는다.
- 글꼴: Noto Sans KR (`next/font/google`).

## BE — `backend/`

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| Spring Boot | 3.5.16 | 포트 8080 |
| Java | 17 | 바이트코드 17 고정 (`options.release = 17`). JDK 17 · 21 모두 빌드 가능 |
| Gradle | 8.14.3 | 래퍼(`gradlew`) 포함, 따로 설치 불필요 |
| MyBatis (`mybatis-spring-boot-starter`) | 3.0.5 | 매퍼 XML은 `src/main/resources/mapper/` |
| springdoc-openapi | 2.8.17 | Swagger UI `/swagger-ui.html` |
| MySQL Connector/J | 9.5.0 | |
| Spring Validation · Lombok | Boot 관리 버전 | |

- 쓰지 않음: JPA, Spring Security (P0 로그인 없음).
- `AI_MOCK=true`(기본)면 AI 서버 없이 `resources/ai-mock/*.json` 고정 응답을 쓴다.
- 설정은 `backend/.env` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AI_SERVER_URL`, `AI_MOCK`, `SEOUL_OPENAPI_KEY`, `FRONTEND_ORIGIN`).

## AI — `ai/`

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| Python | 3.11 | `.venv` 가상환경 |
| FastAPI | 0.162.0 | 포트 8000, 문서 `/docs` |
| uvicorn[standard] | 0.54.0 | |
| pydantic | 2.14.1 | JSON 형식 검증, camelCase alias |
| pydantic-settings | 2.16.1 | `ai/.env` 읽기 |
| google-genai | 2.33.0 | LLM은 Gemini. 호출은 `ai/common/llm.py`에서만 |
| httpx | 0.28.1 | 기업 정보 수집 등 |
| python-dotenv | 1.2.4 | |
| 임베딩 · 리랭커 | 미정 | AI-A가 모델 확정 후 `requirements.txt`에 추가 |

- 설정은 `ai/.env` (`GEMINI_API_KEY`, `LLM_MODEL`, `EMBEDDING_MODEL`, `RERANKER_MODEL`).

## DB

| 기술 | 버전 | 비고 |
| --- | --- | --- |
| MySQL | 8.0 | 포트 3306, 데이터베이스 `ieum`, utf8mb4, 테이블 17개 |
| 접속 계정 | `ieum` | `ieum` DB에만 권한. root는 스키마 재생성 때만 |
| MySQL Workbench | 8.0 | 조회용, 선택 |

- 스키마 `db/schema.sql`, 시드 `db/seed.sql`(수료생 50명). 컬럼 정의는 `docs/DATA_SPEC.md`.
- Docker 쓰지 않음. 각자 PC에 MySQL 설치.

## 협업 도구

| 도구 | 비고 |
| --- | --- |
| Git · GitHub | `team-ieum-2026/ieum`, 1인 1브랜치, PR + merge commit (`docs/GIT_RULES.md`) |
| VS Code | 추천 확장: Extension Pack for Java, Spring Boot Extension Pack, Python, ESLint, Tailwind CSS IntelliSense (`.vscode/extensions.json`) |
| 디스코드 | 공지 · 키 전달(비공개 채널) |

## 쓰지 않는 것

Docker · JPA · Spring Security · 로그인 · 배포 · 실제 알림 발송 · 반응형 UI
