# ai/data/eval — 매칭 평가용 공고 · 기업 데이터

주인: AI-B. 평가(추천 · 기업 정보) 실험에 쓰는 입력 데이터. 정답 라벨은 정답 힌트가 되지 않도록 올리지 않는다.

| 파일 | 건수 | 출처 | 가상 여부 | source | id 범위 |
| --- | --- | --- | --- | --- | --- |
| `ieum_jobs_20261028.json` | 138 | 서울 열린데이터광장 recMntList 2026-10-28 수집분 (IT 직종 코드 24개 · 서울 · 경기 · 인천). BE-A 배치 결과 | 실제 공고 | 없음 (필드 없음) | `jobPostingId` 613 ~ 1389 (PM PC DB 기준 번호) |
| `jobs_general.json` | 150 | AI-B 가 만든 평가용 공고 (위 실제 공고와 같은 필드) | 가상 | `"EVAL"` | `jobPostingId` 900001 ~ 900150 |
| `companies.json` | 50 | AI-B 가 만든 평가용 기업 정보 (`company_info` 형식 + `size` · `industry` · `isNew`) | 가상 | 해당 없음 | id 없음 (`companyName` 으로 연결) |

- `jobs_general.json` 의 회사는 모두 `companies.json` 의 가상 회사다. 근무지는 모두 수도권(서울 · 경기 · 인천), 주소는 '모의동' 가짜 주소.
- `companies.json` 앞 13곳은 `ai/data/company/company_info.json` 의 기존 가상 기업과 이름 · 정보가 같다. URL 은 모두 `example.com`, 최근 이슈는 '(가상)' 표시.
- 세 파일 모두 본문 연락처는 `[연락처 생략]` 으로 가려져 있다.
- `ieum_jobs_20261028.json` 은 원본에서 개인정보 한 곳만 바꿨다: 647번 본문의 상담사 실명 → `[이름 생략] 상담사`.
- AI-A 평가용 공고 id 는 900201 ~ 900300 을 쓴다 (이 폴더 파일과 겹치지 않음).
