# 이음 — 보고하지 않아도, 기회가 먼저 찾아온다

청년취업사관학교 수료생 사후관리 AI 에이전트 · 2026 청년취업사관학교 해커톤 이음팀

- 수료생: 역량에 맞는 공고 Top-3를 먼저 받고, 지원 · 패스 · 도움 요청 중 하나만 누르면 됩니다. 지원하면 근거가 연결된 이력서 · 자기소개서 초안이 만들어집니다.
- 코디네이터: 응답이 곧 상태 데이터가 되어, 도움 요청과 1주 무응답자만 먼저 확인합니다.

## 구조

```
ieum/
├─ frontend/   Next.js (TypeScript · Tailwind)   :3000   FE-A · FE-B
├─ backend/    Spring Boot 3 (Java 17 · MyBatis)  :8080   BE-A · BE-B
├─ ai/         FastAPI (Python 3.11)              :8000   AI-A · AI-B
├─ db/         schema.sql · seed.sql
└─ docs/       요구사항 · API · ERD · 와이어프레임 · 흐름도 · Git 규칙
```

FE → BE → AI 순서로만 호출합니다. FE는 AI 서버를 직접 부르지 않습니다.

## 처음 한 번 설치

| 도구 | 버전 | 확인 명령 |
| --- | --- | --- |
| Git | 최신 | `git --version` |
| Node.js | 20 LTS | `node -v` |
| JDK | 17 이상 (21도 됨. 빌드 결과는 17로 고정) | `java -version` |
| Python | 3.11 | `python --version` (맥: `python3`) |
| MySQL | 8.0 | `mysql --version` |
| VS Code | 최신 | 확장: Extension Pack for Java, Spring Boot Extension Pack, Python, ESLint, Tailwind CSS IntelliSense |

Docker는 쓰지 않습니다. Gradle은 저장소의 `gradlew`가 알아서 받습니다.

## 실행 방법

### 0. 키 · 설정 파일 만들기

```bash
cp frontend/.env.example frontend/.env.local
cp backend/.env.example  backend/.env
cp ai/.env.example       ai/.env
```

키 값(`SEOUL_OPENAPI_KEY`, `GEMINI_API_KEY`)은 디스코드 비공개 채널에서 받아 채웁니다. **`.env` 파일은 절대 커밋하지 않습니다.** `backend/.env`의 `DB_PASSWORD`에는 아래 1번에서 만드는 `ieum` 계정의 비밀번호를 적습니다.

### 1. DB

처음 한 번, root로 `ieum` DB 전용 계정을 만듭니다. 이후 Workbench · 백엔드는 root가 아니라 이 계정으로 접속합니다 (다른 DB는 보이지 않음).

```bash
mysql -u root -p < db/schema.sql          # DB와 테이블 생성 (ieum)
mysql -u root -p ieum < db/seed.sql       # 시드 수료생 50명 (평가용 20 · 명단용 30)
mysql -u root -p -e "CREATE USER IF NOT EXISTS 'ieum'@'localhost' IDENTIFIED BY '내비밀번호'; GRANT ALL PRIVILEGES ON ieum.* TO 'ieum'@'localhost';"
```

- 윈도우에서 `mysql`이 안 잡히면 `"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"` 전체 경로로 실행합니다.
- 스키마가 바뀌었다는 공지를 받으면 `mysql -u root -p -e "DROP DATABASE ieum"` 후 위 두 줄을 다시 실행합니다 (계정은 그대로 유지됨).

### 2. BE (backend, 8080)

```bash
cd backend
./gradlew bootRun          # 윈도우: gradlew.bat bootRun
```

- 상태 확인: http://localhost:8080/api/health
- API 문서(Swagger): http://localhost:8080/swagger-ui.html
- `AI_MOCK=true`(기본)면 AI 서버 없이도 고정 응답으로 동작합니다.

### 3. AI (ai, 8000)

```bash
cd ai
python -m venv .venv
source .venv/bin/activate      # 윈도우: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

- `ai/.env.example`을 `ai/.env`로 복사하고 `GEMINI_API_KEY` · 모델 이름을 채웁니다(키는 디스코드 비공개 채널).
- `requirements.txt`에 리랭커용 torch가 포함돼 처음 설치는 수 GB · 수 분 걸립니다.
- 상태 확인: http://localhost:8000/ai/health
- API 문서: http://localhost:8000/docs

### 4. FE (frontend, 3000)

```bash
cd frontend
npm install
npm run dev
```

- http://localhost:3000 → R0 역할 선택
- `NEXT_PUBLIC_USE_MOCK=true`(기본)면 BE 없이 `public/mock/` 목업으로 동작합니다.

### 내 파트만 띄우면 되는 경우

| 파트 | 띄울 것 |
| --- | --- |
| FE-A · FE-B | FE만 (목업 사용) |
| BE-A · BE-B | DB + BE (`AI_MOCK=true`) |
| AI-A · AI-B | AI만 (Swagger `/docs`에서 단독 테스트) |
| 통합 (11/2~) | DB + AI + BE + FE, `USE_MOCK=false`, `AI_MOCK=false` |

진짜 AI를 처음 붙일 때(`AI_MOCK=false`)는 AI 서버를 켠 뒤 Swagger에서 `POST /api/admin/jobs/reindex`를 한 번 실행합니다. AI 색인이 각자 PC에 따로 쌓여서, 이걸 해야 AI가 공고를 알고 추천합니다. 공고 배치(`sync`)는 매일 06:00에 BE 서버가 켜져 있을 때만 돌고, 꺼져 있었으면 `POST /api/admin/jobs/sync`를 직접 실행합니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 협업 규칙 · 폴더 주인 · 공용 파일 (AI 도구가 먼저 읽음) |
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 파트별 요구사항 체크리스트 (ID · 완료 기준) |
| [docs/API.md](docs/API.md) | API 명세 · 공통 JSON 규칙 · 핵심 JSON 형식 |
| [docs/ERD.md](docs/ERD.md) | 테이블 관계도 · 테이블 주인 |
| [docs/DATA_SPEC.md](docs/DATA_SPEC.md) | 데이터 명세서 (테이블 17개 컬럼 정의 · 상태 판정 규칙) |
| [docs/TECH_STACK.md](docs/TECH_STACK.md) | 기술 스택 · 버전 · 호출 방향 |
| [docs/JOB_API.md](docs/JOB_API.md) | 서울 채용정보 API 필드 매핑 · IT 직종 필터 · 배치 규칙 |
| [docs/ROLES.md](docs/ROLES.md) | 역할 분담 · 2주 일정 · 위험과 대응 |
| [docs/CHANGES.md](docs/CHANGES.md) | 기획서 대비 변경사항 |
| [docs/GIT_RULES.md](docs/GIT_RULES.md) | 브랜치 · 커밋 · PR · 막혔을 때 |
| `docs/wireframe/` | 와이어프레임 (전체 화면 / 화면 전환 프로토타입) · 브라우저로 열기 |
| `docs/flow/` | P0 흐름도 S1~S13 · 브라우저로 열기 |
| [docs/SETUP_GUIDE.md](docs/SETUP_GUIDE.md) | (PM용) 첫 셋팅 · GitHub 설정 절차 |

## 일정

- 10/26~11/1 병렬 개발 (매일 `dev`로 PR, 병합 담당 3명이 병합 · `main`은 동작 확인된 최종본만) · 10/29 얇은 연결 1회
- 11/2~11/4 통합 · 11/4 저녁 코드 프리즈 (`demo-1104` 태그)
- 11/6 23:59 제출 (IR Deck + 시연영상 2분) · 11/9 본선 (DDP)
