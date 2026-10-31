# ai/data/eval — 매칭 평가용 공고 · 기업 데이터

주인: AI-B. 평가(추천 · 기업 정보) 실험에 쓰는 입력 데이터. 정답 라벨은 정답 힌트가 되지 않도록 올리지 않는다.

| 파일 | 건수 | 출처 | 가상 여부 | source | id 범위 |
| --- | --- | --- | --- | --- | --- |
| `ieum_jobs_20261028.json` | 138 | 서울 열린데이터광장 recMntList 2026-10-28 수집분 (IT 직종 코드 24개 · 서울 · 경기 · 인천). BE-A 배치 결과 | 실제 공고 | 없음 (필드 없음) | `jobPostingId` 613 ~ 1389 (PM PC DB 기준 번호) |
| `jobs_general.json` | 150 | AI-B 가 만든 평가용 공고 (위 실제 공고와 같은 필드) | 가상 | `"EVAL"` | `jobPostingId` 900001 ~ 900150 |
| `companies.json` | 50 | AI-B 가 만든 평가용 기업 정보 (`company_info` 형식 + `size` · `industry` · `isNew`) | 가상 | 해당 없음 | id 없음 (`companyName` 으로 연결) |
| `eval_jobs.sql` | 150 | `jobs_general.json` 을 `job_posting` 에 넣는 SQL | 가상 | `'EVAL'` | `id` 900001 ~ 900150 |

- `jobs_general.json` 의 회사는 모두 `companies.json` 의 가상 회사다. 근무지는 모두 수도권(서울 · 경기 · 인천), 주소는 '모의동' 가짜 주소.
- `companies.json` 앞 13곳은 `ai/data/company/company_info.json` 의 기존 가상 기업과 이름 · 정보가 같다. URL 은 모두 `example.com`, 최근 이슈는 '(가상)' 표시.
- 세 파일 모두 본문 연락처는 `[연락처 생략]` 으로 가려져 있다.
- `ieum_jobs_20261028.json` 은 원본에서 개인정보 한 곳만 바꿨다: 647번 본문의 상담사 실명 → `[이름 생략] 상담사`.
- AI-A 평가용 공고 id 는 900201 ~ 900300 을 쓴다 (이 폴더 파일과 겹치지 않음).

## eval_jobs.sql

실행: `mysql -u <계정> -p ieum < ai/data/eval/eval_jobs.sql` (`db/schema.sql` · `db/seed.sql` 뒤). 빼기: `DELETE FROM job_posting WHERE source = 'EVAL';`

- 칸 · 형식은 BE 배치(`SeoulJobParser` · `JobPostingMapper.upsert`)와 같다. 다시 실행해도 `ON DUPLICATE KEY UPDATE` 로 같은 id 를 덮어써 중복이 생기지 않는다.
- `external_id` 는 seed 의 `'dev-501'` 처럼 `'eval-<id>'`. `raw` 는 API 원본이 없어 NULL.
- `content_hash` 는 BE 와 같이 SHA-256(description) 으로 다시 계산했다. `jobs_general.json` 의 `contentHash` 는 150건 모두 이 값과 다르다 (현재 본문의 SHA-256 이 아님).
- `job_code` 는 `application.yml` `it-job-codes` 의 코드. IT 코드 목록 밖 직종 14건(보안 관제원 · IT 기술영업원 등, 일부러 넣은 비IT 공고)은 NULL.
- `is_active` 는 마감일이 2026-10-29 이후면 1 (150건 모두 1). BE 배치는 source `SEOUL_API` 공고만 끄므로 EVAL 공고는 켜진 채 남는다.
- id 를 직접 넣으므로 `job_posting` AUTO_INCREMENT 가 900151 로 올라가, 이후 배치 공고 id 가 900151 부터 붙는다.
