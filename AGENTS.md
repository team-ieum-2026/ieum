# AGENTS.md — 이음 P0 협업 규칙

이 파일은 이 저장소에서 일하는 모든 AI 코딩 도구(Claude Code, Codex, Cursor 등)가 **가장 먼저 읽는 규칙**입니다. 사람도 같은 규칙을 따릅니다.

## 프로젝트 한 줄

수료생 사후관리 AI 에이전트 "이음". 보고하지 않아도, 기회가 먼저 찾아온다.
2026 청년취업사관학교 해커톤 · P0 기간 10/26~11/1 병렬 개발, 11/2~11/4 통합, 11/4 코드 프리즈.

## 먼저 읽을 문서

| 알고 싶은 것 | 파일 |
| --- | --- |
| 내가 할 일 · 완료 기준 | `docs/REQUIREMENTS.md` |
| API 경로 · 요청 · 응답 · JSON 규칙 | `docs/API.md` |
| 테이블 · 컬럼 | `db/schema.sql`, `docs/ERD.md` |
| 공고 API 필드 매핑 | `docs/JOB_API.md` |
| 화면 모양 | `docs/wireframe/` |
| 흐름 S1~S13 | `docs/flow/` |
| Git 사용법 | `docs/GIT_RULES.md` |

## 반드시 지킬 규칙

1. **자기 담당 폴더 밖의 파일은 수정하지 않는다.** 아래 "폴더 주인" 표를 보고, 사용자의 파트(FE-A 등)가 주인이 아닌 파일이면 수정하지 않는다. 사용자의 파트를 모르면 먼저 묻는다.
2. **공용 파일(아래 표)을 수정해야 하면 작업 전에 멈추고 사용자에게 "파일 주인(○○)에게 확인했나요?"라고 먼저 묻는다.** 확인했다고 답하기 전에는 수정하지 않는다.
3. **다음은 사용자 확인 없이 하지 않는다:** 라이브러리 추가 · 버전 변경, DB 스키마 변경, JSON 형식 변경, API 경로 · 요청 · 응답 형식 변경.
4. **API 요청 · 응답은 `docs/API.md`의 공통 JSON 규칙을 따른다.** camelCase 키, UPPER_SNAKE 상태값, 성공 `{ "data": ... }`, 실패 `{ "error": { "code", "message" } }`, 값이 없으면 키를 빼지 않고 `null`.
5. **`.env`와 API 키는 커밋하지 않는다.** 키를 코드 · 문서 · 로그에 쓰지 않는다. 예시는 `.env.example`에만, 값은 비워 둔다.
6. **절대 실행하지 않는 명령:** `git push --force`(`-f` 포함), `git reset --hard`, `git clean -fd`, `main` 브랜치에 직접 커밋 · push. 사용자가 원하더라도 위험을 설명하고 PM에게 먼저 묻도록 안내한다.
7. **실명 · 연락처 · 성적 등 실제 개인정보를 코드 · 시드 · 목업에 넣지 않는다.** 모든 데이터는 모의 데이터.
8. **PR 설명에 완료한 요구사항 ID(예: `FE-A-03`)와 공용 파일 수정 여부를 적는다.**
9. P0에 없는 기능(로그인, 배포, 실제 알림 발송, Docker 등)은 만들지 않는다. 필요해 보이면 사용자에게 제안만 한다.

## 기술 스택 (변경 금지)

- FE: TypeScript · Next.js App Router(React) · Tailwind CSS · Node 20 LTS · npm (yarn/pnpm 금지)
- BE: Spring Boot 3 · Java 17 · Gradle · MyBatis · springdoc(Swagger) · JPA 사용하지 않음
- AI: FastAPI · Python 3.11 · pydantic · LLM은 Gemini(공용 모듈 `ai/common/`에서만 호출)
- DB: MySQL 8.0 (utf8mb4) · Docker 사용하지 않음
- 포트: FE 3000 · BE 8080 · AI 8000. **FE는 BE만 호출하고, AI 서버는 BE만 호출한다.**

## 폴더 주인

| 폴더 · 파일 | 주인 |
| --- | --- |
| `frontend/src/app/page.tsx` (R0) | FE-A |
| `frontend/src/app/student/` (M1~M5, M7, 하단 메뉴 layout) | FE-A |
| `frontend/src/app/student/applications/[applicationId]/` (M6 · M6-1) | FE-B |
| `frontend/src/app/student/profile/` (M8) | FE-B |
| `frontend/src/app/coordinator/layout.tsx`, `coordinator/page.tsx` (C1), `coordinator/applications/` (C4) | FE-B |
| `frontend/src/app/coordinator/students/` (C2 · C3) | FE-A |
| `frontend/src/components/ui/`, `frontend/src/components/student/`, `frontend/src/styles/` (디자인 토큰) | FE-A |
| `frontend/src/components/resume/`, `frontend/src/components/coordinator/` | FE-B |
| `frontend/src/lib/api/student.ts`, `coordinatorStudents.ts` · `frontend/src/types/student.ts` | FE-A |
| `frontend/src/lib/api/resume.ts`, `profile.ts`, `coordinatorReview.ts` · `frontend/src/types/resume.ts`, `profile.ts`, `coordinator.ts` | FE-B |
| `backend/.../common/` (응답 · 예외 · CORS · Jackson), `application.yml` | BE-A |
| `backend/.../job/`, `recommendation/`, `application/`, `ai/` + 같은 이름의 `resources/mapper/` | BE-A |
| `backend/.../profile/`, `helprequest/`, `status/`, `coordinator/`, `domain/`, `common/mybatis/` + 같은 이름의 `resources/mapper/` | BE-B |
| `db/schema.sql`, `db/seed.sql`, `docs/ERD.md` | BE-B |
| `ai/main.py`, `ai/common/`, `ai/schemas/`, `ai/document/`, `ai/eval/` | AI-A |
| `ai/profile/`, `ai/matching/`, `ai/company/`, `ai/data/` | AI-B |
| `docs/`(ERD 제외, `JOB_API.md`는 BE-A), `AGENTS.md`, `CLAUDE.md`, `README.md`, `.github/`, `.gitignore`, `frontend/public/mock/` | PM |
| 의존성 파일 `package.json` · `package-lock.json` · `build.gradle` · `requirements.txt` | PM |

## 공용 파일 (수정 전 주인 확인 필수)

| 파일 | 주인 | 규칙 |
| --- | --- | --- |
| 디자인 토큰 · 공통 부품 `components/ui/` | FE-A | FE-B는 직접 고치지 않고 요청. 필요한 변형은 props로 추가 |
| 하단 메뉴 (`app/student/layout.tsx`) | FE-A | M8 탭 추가도 FE-A가 처리 |
| 코디네이터 레이아웃 · 사이드바 | FE-B | FE-A는 C2 · C3 화면만 작성 |
| FE API 함수 · 타입 | 위 표대로 도메인별 분리 | 한 파일에 둘이 쓰지 않음 |
| BE 도메인 클래스 · DB 스키마 | BE-B | BE-A가 컬럼이 필요하면 BE-B에 요청. `application` 테이블은 BE-A가 생성 · 제출, BE-B가 승인 · 반려 · 전달만 변경 |
| BE 공통 설정 · `application.yml` | BE-A | 설정 추가 시 PR 설명에 명시 |
| `ai/main.py` 라우터 등록 | AI-A | 라우터는 이미 등록됨. 각자 자기 `router.py`만 수정 |
| `ai/common/` (LLM · 임베딩 · 리랭커 · 설정) | AI-A | 모델 · 키 설정은 여기 한 곳. AI-B는 import만 하고 변경은 AI-A에 요청 |
| AI 데이터 형식 `ai/schemas/` · `docs/API.md` 3장 | AI-A | AI-B는 형식에 맞춰 데이터만 채움 |
| 의존성 파일 | PM | 라이브러리 추가는 디스코드에 먼저 알리고 `chore/` 브랜치 PR 하나로 따로 올림 |
| 시드 데이터 | BE-B 적재 · AI-B 생성 | AI-B는 정해진 형식의 파일만 넘기고, 적재 스크립트는 BE-B만 수정 |

## 파트 · 담당자

| 파트 | 담당 | 브랜치 |
| --- | --- | --- |
| FE-A | 김하윤 | `fe-a/student-ui` |
| FE-B | 유지수 | `fe-b/resume-dashboard` |
| BE-A (PM) | 김용현 | `be-a/student-api` |
| BE-B | 유능한 | `be-b/coordinator-api` |
| AI-A (AI 리드) | 심소민 | `ai-a/document` |
| AI-B | 조영동 | `ai-b/recommend` |

## 코드 작성 습관

- 커밋 메시지: `종류(화면번호): 한 일` · 종류는 `feat` `fix` `style` `chore` · 예: `feat(M1): 공고 카드 3장 표시`
- 수료생 화면은 폭 390px 고정, 코디네이터 화면은 데스크톱 고정. 반응형은 만들지 않는다.
- 수료생 화면 문구에 "취업 여부", "보고"를 묻는 표현을 쓰지 않는다.
- 색은 디자인 토큰(Tailwind 테마)만 쓰고 색값을 화면 코드에 직접 쓰지 않는다.
- FE는 `NEXT_PUBLIC_USE_MOCK=true`일 때 `frontend/public/mock/`의 목업을 쓴다. 실제 API로 바꿀 때 화면 코드는 바뀌지 않아야 한다.
- BE는 AI 서버가 준비되기 전까지 `ai/` 패키지의 클라이언트에서 고정 응답을 돌려준다.
