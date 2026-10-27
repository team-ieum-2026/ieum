# 공고 API 필드 매핑 (BE-A)

서울 열린데이터광장의 "서울시 일자리포털 채용 정보"를 쓰고, 일 배치로 `job_posting`에 저장합니다. 이 문서는 2026-10-24에 받은 실제 응답을 기준으로 썼습니다.

## 호출

```
http://openapi.seoul.go.kr:8088/{SEOUL_OPENAPI_KEY}/json/recMntList/{시작}/{끝}/
```

- 서비스명: **`recMntList`**
- 한 번에 최대 1,000건씩 받습니다. 2026-10-24 기준 전체 42,141건이라 하루 43번 정도 호출하면 됩니다.
- 응답 모양: `{ "recMntList": { "list_total_count": 42141, "RESULT": { "CODE": "INFO-000", "MESSAGE": "..." }, "row": [ ... ] } }`
- 정상이면 `RESULT.CODE`가 `INFO-000`입니다. `ERROR-500`은 서버 쪽 일시 오류이므로 그날 배치는 건너뛰고 기존 데이터를 그대로 씁니다.
- 실제 응답 5건 예시: `docs/samples/recMntList_sample.json` (2026-10-24 수집, 연락처는 비움). 배치 파싱 테스트에 그대로 씁니다.
- 키 없이 형식만 보고 싶으면 `sample` 키로 5건까지 받을 수 있습니다: `.../sample/json/recMntList/1/5/`

## 주의할 점

1. **공고 ID가 없습니다.** 구인신청번호 같은 고유 번호가 응답에 없어서, `external_id`는 `SHA-1(COMPANY|TITLE|REG_DT|STRTNM_CD)`로 만듭니다.
2. **같은 공고가 근무지만 다르게 여러 줄로 옵니다.** 회사 · 제목 · 등록일이 같고 `STRTNM_CD`, `WORK_REGION`만 다른 경우입니다. DB에는 각각 저장하고, 추천 결과에서는 회사명과 제목이 같으면 하나만 보여 줍니다(AI-B 매칭에서 처리).
3. **대부분이 IT 직종이 아닙니다.** 돌봄, 요양, 물류 공고가 많습니다. 배치에서 IT 직종만 남깁니다(아래 "IT 직종 필터").
4. **서울 밖 공고도 섞여 있습니다.** 예: `REGION: 경기 군포시`. 지역은 거르지 않고 저장하며, 선호 지역은 매칭 단계에서 반영합니다.
5. **마감일이 "채용시까지 26-11-06"처럼 올 수 있습니다.** 그 날짜 전에 마감될 수도 있다는 뜻입니다. 날짜만 꺼내 `deadline_on`에 넣고 `until_filled = 1`로 표시합니다.
6. **원문 링크가 없습니다.** M2 화면에는 링크 대신 출처 문구 "서울 열린데이터광장 · 서울시 일자리포털 채용정보"만 표시합니다.
7. `CONTACT_TELNO`(회사 전화번호)는 `raw`에만 남기고 화면과 API에는 내보내지 않습니다.

## 필드 매핑

| API 필드 | 예시 | `job_posting` 컬럼 | 변환 |
| --- | --- | --- | --- |
| `COMPANY` | 해와달케어 | `company_name` | 그대로 |
| `TITLE` | 산모.신생아 건강관리사, 육아도우미 모집 | `title` | 그대로 |
| `JOBS_CD` | 550500 | `job_code` | 그대로 (IT 필터에 사용) |
| `JOBS_NM` | 육아 도우미(베이비시터)(550500) | `job_category` | 끝의 `(코드)` 제거 |
| `REGION` | 서울 노원구 | `location` | 그대로 |
| `WORK_REGION` | (01760) 서울특별시 노원구 덕릉로 517 … | `work_address` | 앞의 `(우편번호)` 제거 |
| `EMP_TP_NM` | 기간의 정함이 없는 근로계약/ 파견근로 비희망/ … | `employment_type` | "기간의 정함이 없는" → `정규직`, "기간의 정함이 있는" → `계약직`, "(시간(선택)제)"가 있으면 ` · 시간제`를 붙임, 둘 다 아니면 첫 `/` 앞부분 |
| `CAREER` | 관계없음 / 신입 / 경력 | `career` | 그대로 |
| `MIN_EDUBG` | 학력무관 | `education` | 그대로 |
| `SAL_TP_NM` | 월급3,430,000원 이상 ~ 3,430,000원 이하, | `salary_text` | 끝의 쉼표 제거 |
| `REG_DT` | 26-09-08 | `posted_on` | `YY-MM-DD` → `20YY-MM-DD` |
| `CLOSE_DT` | 채용시까지 26-11-06 / 26-11-13 | `deadline_on`, `until_filled` | 마지막 `YY-MM-DD`를 날짜로. "채용시까지"로 시작하면 `until_filled = 1` |
| 아래 "본문" 항목 | | `description` | 합쳐서 저장 |
| 응답 한 건 전체 | | `raw` | JSON 그대로 |
| (계산) | | `content_hash` | `description`의 SHA-256. 바뀌면 AI 색인 다시 계산 |

## 매칭용 본문 (`description`)

AI-B가 임베딩 · 리랭커에 쓰는 글입니다. 값이 빈 줄은 뺍니다.

```
[직무] {job_category}
[내용] {JOB_CONT}
[필요 역량] {COMP_ABL}
[자격증] {CERTIFICATE}
[전공] {MAJOR}
[우대 조건] {PF_COND}
[근무 시간] {WORKDAY_WORKHR_CONT}
[전형] {SEL_MTHD}
```

## IT 직종 필터

배치는 `application.yml`의 `app.seoul.it-job-codes`(JOBS_CD 목록)에 있는 공고만 저장합니다. 목록이 비어 있을 때만 아래 단어 필터로 대신합니다.

- `JOBS_NM`에 다음 단어 중 하나가 들어감: `소프트웨어`, `웹`, `앱`, `프로그래머`, `개발자`, `데이터`, `정보시스템`, `네트워크`, `보안`, `인공지능`, `IT`, `시스템 운영`

**단어 필터를 기본으로 쓰지 않는 이유:** 2026-10-25 전체 분포를 보니 단어 필터에 걸리는 264건 중 절반 이상이 IT가 아니었습니다(보안 관제원 44 · 경호 및 보안 40 · 감시카메라 설치 10 · 여행 상품 개발자 4 · IT 기술영업 13 등). 그래서 코드 목록을 기준으로 바꿨습니다.

**첫 배치 결과 (2026-10-25):** 전체 41,191건 → IT 코드 23개로 105건 저장 (서울 근무지 56 · 신입 15 · 경력 무관 38 · 경력 52 · 채용 시 마감 73). 코드 목록과 이름은 `application.yml` 주석에 있습니다. 웹 디자이너(415501, 약 40건)는 개발 직무가 아니라 뺐고, 추천할 공고가 부족하면 가장 먼저 넣을 후보입니다.

## AI 색인 요청 (BE-A-03, AI-B와 확정 필요)

배치 뒤 새로 들어오거나 `content_hash`가 바뀐 활성 공고만 AI `POST /ai/matching/index`에 보냅니다. 본문은 BE-A 초안입니다.

```json
{ "jobs": [ { "jobPostingId": 613, "contentHash": "…64자", "companyName": "…", "title": "…",
              "jobCategory": "응용 소프트웨어 개발자", "location": "서울 구로구", "career": "신입", "description": "[직무] …" } ] }
```

## 배치 규칙

1. 하루 1회 실행합니다. 시연용으로는 수동 실행 엔드포인트 `POST /api/admin/jobs/sync`도 둡니다(P0, 인증 없음).
2. 전체 페이지를 받아 IT 필터를 적용한 뒤, `(source, external_id)` 기준으로 upsert합니다.
3. 이번에 받은 목록에 없거나 `deadline_on`이 지난 공고는 `is_active = 0`으로 바꿉니다. 지원 기록이 깨지지 않게 삭제는 하지 않습니다.
4. `content_hash`가 바뀌거나 새로 들어온 공고 ID만 모아 AI `POST /ai/matching/index`에 보냅니다.
5. 첫 배치가 성공하면 시드의 개발용 임시 공고를 끕니다: `UPDATE job_posting SET is_active = 0 WHERE source = 'DEV_SEED';`
6. API가 실패하면(`ERROR-500`, 시간 초과) 그날은 건너뛰고 기존 데이터로 추천합니다.
