# 이음 수료생 디자인 시스템

## 적용 범위

R0 및 수료생 M1~M8. 기존 API 계약·데이터 모델·studentId 전달·업무 상태·저장 시점을 유지한다. 도움 요청 조회·개별 종료·유형 선택과 현재 상태 선택은 기존 BE API에 연결하며 필요한 학생 도메인 타입을 추가한다. 코디네이터 전용 화면과 테마는 유지한다.

| 파일 | 역할 |
| --- | --- |
| tokens.css | career 색·그림자 토큰의 단일 정의 |
| career-foundation.css | 수료생·R0 내부의 테마 연결, 로컬 Pretendard, 이미지·포커스·모션 |
| student.css | 상단 내비게이션·보조 사이드바·수료생 전체 페이지 |
| entry.css | 역할 선택·ID 입력·도움말 |
| components/student/CareerImage.tsx | 로컬 이미지·반응형 sizes·로드 실패 fallback |
| components/resume/BottomSheet.tsx | 기존 공유 문서 시트의 포커스 순환·복귀·Esc·스크롤 제한 |

기존 recommendation-layout/polish CSS의 중복 import와 zoom 기반 배율을 제거했다.

## 시각 기준

| 항목 | 기준 |
| --- | --- |
| 콘텐츠 | 최대 1240px, 가운데 정렬 |
| 여백 | 4/8/12/16/20/24/32/40/48/64/80px |
| 제목 | 사용자 조정값: R0 77~89px / M1 데스크톱 47px·모바일 39px / 일반 페이지 32px / 섹션 24px / 카드 20px |
| 본문 | 주 본문 16px / 보조 14px / 캡션 12~13px |
| 글꼴 | 수료생·R0: 로컬 Pretendard Variable, 기존 Noto Sans KR 폴백 |
| 행동 | 주 CTA 파랑, 보조 흰색·중립색, 최소 높이 44px |
| 대비 | 밝은 Blue는 강조, Deep Blue는 작은 글씨와 흰 글씨 CTA |
| AI·추천 | 보라색과 출처 문구 |
| 응답 | 지원 파랑 / 패스 보라 / 도움 요청 빨강 |
| 지원서 | 생성·초안 보라 / 검토 파랑 / 승인·전달 초록 / 보완 주황 / 실패 빨강 |
| 이미지 | 공고 카드·상세에 로컬 WebP, next/image, 반응형 sizes. 출처는 이 문서에 기록 |
| 모션 | 150~300ms 호버, reduced-motion에서는 전환·애니메이션 제거 |

## 반응형·탐색

- Desktop 1024px 이상: 상단 3메뉴, 공고 3열, 상세·문서의 보조 행동 패널.
- Tablet 640~1023px: 공고 2열, 상세·서류·근거는 읽기 편한 세로 배치.
- Mobile 639px 이하: 공고 1열, safe area가 있는 하단 3탭, 하단 편집 시트.
- 작은 화면에서도 홈 복귀 문구와 접근성 이름을 유지.
- 사이드바는 모든 크기에서 열기·닫기 가능. 프로필·도움 요청·홈 복귀도 유지.
- 편집 화면은 이미지보다 문서와 근거·입력 효율을 우선한다.
- R0는 이미지 없는 중앙 정렬 화면이다. 제목은 글자별 페이드인, 첫 줄 완료 후 0.25초 뒤 둘째 줄을 표시한다. 모션 감소 설정에서는 즉시 표시한다. 역할 카드·ID 검증·ID 도움말을 유지한다.
- M1은 실제 이름·추천 수를 통합한 헤더를 사용한다. 0건은 별도 문구, 로딩 중에는 건수를 표시하지 않는다. 별도 인사말·Hero 배너·장식 영문을 제거했다. 카드 테두리는 최초의 연한 공통 테두리 토큰을 사용한다.
- 추천 날짜·기준은 새로 추천받기 버튼 위에 오른쪽 정렬한다. 데스크톱 카드 Hover는 100ms 대기 후 내부 이미지와 추천 이유 영역을 재배치하며 주변 카드 높이를 바꾸지 않는다. 키보드 포커스에는 대기 없이 표시하고 터치 화면에는 명시적인 추천 이유 버튼을 제공한다.

## 기능 보존 체크리스트

| 화면 | 유지 기능 |
| --- | --- |
| R0 | 역할 선택, ID 검증·존재 확인·도움말, 닫기/Esc/배경 클릭, 처리 중 제한 |
| 공통 | 메뉴 3개·현재 위치, sidebar 토글, 로고·나의 커리어, 프로필·도움 요청·역할 홈, studentId. 생성 전 유형 선택과 공고 없는 요청 지원 |
| M1 | 추천 0~3건, 순위·조건·모든 마감 상태·근거, 갱신·기준·날짜, 조건→프로필, 응답·취소, 초안 이어보기 |
| M2 | 공고 정보·누락 정보·출처, 추천 이유·근거 펼치기, 요약·본문, 지원·패스·도움 요청·취소·중복 제한 |
| M3 | 사유 6종, 기타 메모, 미선택 비활성, 취소·완료·오류 |
| M4 | 요청 완료, 문자·전화·이메일, 선택 메모, 저장·복귀 |
| M5 | 단계 3개·진행률·상태, 2초 조회, 완료 시 M6, 실패·다시 조회·지원현황 |
| M6 | 섹션 9개와 자기소개서, 요약·출처·저장상태, 병역 선택입력, 검토요청 확인·제출, 읽기전용 |
| M6-1 | 문장·출처·AI 번호, 근거 원문/URL/수집일, 수정·USER 전환, 기업정보 안내·복귀 |
| M6-2 | 기존 입력 필드·검증·추가·수정·삭제·삭제 확인·되돌리기, 프로필 동시 저장 선택·원래 화면 복귀 |
| M7 | 집계 3종, 상태 7종+전체 필터·건수, 카드·진행 단계·제출일·수정 수, 상태별 이동, 다른 공고 응답·자동갱신 |
| M8 | 기본·수료정보, AI 역량, 등록자료·원본 자기소개서·프로젝트 CRUD, 파일 첨부 여부, 저장·역량 반영·지연, 실제 DB 상태 조회·선택, 내 도움 요청 접수·전체 조회·개별 종료 |
| 피드백 | 불러오기·처리 중·성공·실패·없음·404·재시도·읽기전용·포커스 |

## 기존 구현과 문서의 차이

1. 적용·추가·삭제 버튼으로 저장하며 자동 저장이라고 표시하지 않는다.
2. 프로필 현재 상태는 기존 GET/PUT /api/students/{studentId}/status에 연결했다. SEEKING·EMPLOYED·RESTING만 선택하며 CHECK_REQUIRED도 서버 값 그대로 표시한다. 브라우저 미리보기 캐시는 사용하지 않는다. 도움 요청은 상태 선택으로 자동 종료하지 않는다.
3. 최신 dev의 응답 취소 DELETE 계약을 사용한다. 실행 중인 BE의 재시작·DB 준비 여부와 별개로 실패 시 기존 응답을 유지한다. 도움 요청 종료 POST는 응답 취소 DELETE와 다른 기능이며 현재 상태·추천 응답을 임의 변경하지 않는다.
4. 생성 실패의 다시 시도는 상태 조회이며 새로운 생성 요청이 아니다.
5. 문서 수정·제출은 DRAFT에서만 가능. REJECTED도 현재 읽기 전용.
6. 포트폴리오는 등록 파일 표시와 첨부 선택만 제공. 업로드 기능을 추가하지 않는다.

## 검증

- 프로덕션 빌드, TypeScript, 변경 파일 ESLint, git diff --check.
- Chrome에서 ID 진입, 사이드바·Esc·포커스 복귀, 패스·취소 실패 보존, 도움 요청·연락 방법·메모.
- 지원 → 생성 → 섹션 9개 → 문장 수정 → 검토 요청 → 지원현황.
- 프로필 상태 실제 DB 조회, 자격증 저장, 입력 중 포커스 유지, 편집창 Esc 복귀.
- 320/390/640/768/1024/1440px 화면의 가로 넘침 확인.
- 실제 DB 읽기 검증 스크립트: components/student/qa/real-db.mjs.
- 현재 스크린샷: components/student/qa/screenshots/. 과거 renewal-* 캡처는 이번 변경의 검증 자료에 포함하지 않는다.
- 최초 디자인 검증 당시 기존 목업·응답 취소 테스트 23개 중 22개 통과. 예전 목업의 시드 상태 비교 1건은 실제 BE 판정 규칙과 달라 실패했다. 현재 실행·검증에는 목업 서버를 사용하지 않는다.

현재 실행은 frontend/.env.local의 NEXT_PUBLIC_USE_MOCK=false, NEXT_PUBLIC_API_BASE_URL=http://localhost:8080을 사용한다. 프런트는 http://localhost:3000에서 열어 기존 BE CORS 허용 주소와 맞춘다. 해당 환경 파일은 커밋하지 않는다.

브라우저 QA는 설치된 Chrome과 Node CDP를 사용한다. 의존성 추가 없음. `node frontend/src/components/student/qa/real-db.mjs`로 6명의 실제 BE/DB 상태와 화면을 비교하고 목업 요청이 없음을 확인한다. 상태 저장 실패 검증은 PUT 요청을 전송 전에 차단해 실제 DB와 선택 이력을 변경하지 않는다. IEUM_QA_ORIGIN·IEUM_QA_API_ORIGIN·IEUM_CHROME_PATH·IEUM_CDP_PORT로 환경을 지정한다. 실패 시 기존 상태를 유지한다. 브라우저 캐시의 미리보기 상태도 무시하는지 검증한다.

`qa/job-card.mjs`는 카드 12개 시나리오, `qa/help-requests.mjs`는 도움 요청 14개 시나리오, `qa/navigation-and-editor.mjs`는 R0·M6·C4 통합 흐름을 확인한다. 이 세 검사는 실제 API 주소의 응답을 해당 Chrome 세션 안에서만 대체하며 성공·실패·409·중복 클릭을 확인한다. 실제 DB 저장 성공 검증과 구분한다.

현재 연결 DB에는 `help_request.help_type`·`close_reason`과 `student_intent_history`가 없어 도움 요청 조회는 500을 반환한다. 상태 GET은 가능하지만 생성·종료·상태 PUT의 실제 저장 성공은 미검증이다. BE-B가 `db/migrations/20261102_student_intent_help_requests.sql`을 적용하고 BE를 재시작해야 한다. FE 작업에서 스키마를 수정하거나 원본 시드를 재적재하지 않는다.

## 글꼴·이미지 출처

- Pretendard 공식: https://github.com/orioncactus/pretendard. SIL OFL, public/fonts/Pretendard-LICENSE.txt 포함.
- Noto Sans KR 공식: https://github.com/google/fonts/tree/main/ofl/notosanskr. 기존과 같은 글꼴을 로컬로 전환해 Google Fonts 네트워크 실패를 해소. 기본 WOFF2는 583,660바이트만 미리 받고, 드문 글자는 3,405,380바이트 확장 WOFF2를 필요할 때 받는다. 원본의 23,174개 문자 매핑과 굵기 100~900을 보존한다. public/fonts/NotoSansKR-LICENSE.txt와 재생성 안내 README.md 포함.
- 기본 이미지 3개와 추가 이미지 4개는 내장 image_gen으로 생성. 외부 기업 사진·로고 없음. WebP는 public/images/career/에 보관. 추가 4종의 원본·프롬프트는 public/images/career/ADDITIONAL_ASSETS.md에 기록한다.
- 이미지가 장식이므로 alt는 빈 값. 사용자 요청에 따라 이미지 위 설명 문구는 표시하지 않으며 생성 출처와 프롬프트는 이 문서에 보관한다.
- 참고: 사용자 Wanted 브리프와 Toss UX 가이드(https://developers-apps-in-toss.toss.im/design/consumer-ux-guide). Wanted 직접 열람은 403으로 제한되어 사용자 제공 기준을 적용했다.

## 이미지 생성 프롬프트

### career-editorial.webp

Create a premium editorial photograph for a Korean career platform website, landscape 3:2 crop. A sunlit modern creative studio with a sculptural cobalt blue curved chair foreground, warm light wooden desk, a closed silver laptop, frosted lavender glass partitions, soft coral desk lamp, bright airy white architectural space, natural daylight, clean magazine art direction, realistic photography, calm optimistic professional mood. No people, no words, no text, no logos, no charts, no UI, no plants as focal point. Wide composition with interesting geometry. Generic workspace, never represent a particular company.

### workspace-digital.webp

Premium editorial interior photograph, landscape 3:2. Beautiful contemporary software studio workspace, pale cool blue color mood, blue chairs, large daylight windows, silver laptop and monitor with screen turned away, white desks, restrained architectural geometry, naturally believable materials, high end commercial photography. No people, no readable text, no logos, no company identity, no UI. A generic shared workspace representing digital careers.

### workspace-creative.webp

Premium editorial interior photograph, landscape 3:2. Contemporary design studio, warm terracotta and muted lavender palette, peach upholstered chair, light oak desk, open notebook, a few colorful material sample books, sculptural white lamp, strong natural morning light and soft shadows, professional architectural magazine photography. No people, no readable text, no logos, no particular company. Generic creative workplace.
