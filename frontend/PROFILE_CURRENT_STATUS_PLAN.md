# 내 프로필 현재 상태 선택 기획

2026-11-01. 대상: M8 내 프로필의 이름 아래 `현재 상태` 영역.

## 현재 구현 범위

프론트엔드에서 상태를 선택·변경하고 학생 ID별로 이 브라우저에 저장한다. 새로고침과 화면 재진입 시 복원하고, 같은 브라우저의 다른 탭에도 반영한다. 서버 저장 API는 추가하거나 호출하지 않는다. 화면에 `미리보기 · 이 브라우저에만 저장돼요`를 표시하며, 선택창에도 담당자 공유·추천·지원 상태에 반영되지 않음을 안내한다.

기존 `StudentStatus`(EMPLOYED/SEEKING/NEED_HELP/CHECK_REQUIRED)는 활동·등록 자료에 따른 서버 자동 판정이다. 수료생이 직접 선택하는 표시와 구분한다. 학력의 status, 경력의 재직 여부, PASS 사유, 도움 요청을 조작해 현재 상태를 저장하지 않는다.

| 선택값 | 표시 | 설명 |
| --- | --- | --- |
| SEEKING | 구직 중 | 나에게 맞는 기회를 찾고 있어요. |
| EMPLOYED | 취업 완료 | 새로운 직장에서 일을 시작했어요. |
| PREPARING | 취업 준비 중 | 학습과 경험을 쌓으며 준비하고 있어요. |
| TAKING_BREAK | 잠시 쉬는 중 | 쉬어가며 다음 걸음을 생각하고 있어요. |
| null | 상태를 선택해주세요 | 아직 선택하지 않음. 임의로 구직 중으로 판정하지 않음 |

`선택하기/변경` → 라디오 선택창 → `적용`. 닫기·Esc는 선택을 저장하지 않는다. 저장하지 못하면 오류와 입력을 유지하며 기존 상태를 바꾸지 않는다. localStorage 키는 `ieum:profile-status-preview:v1:{studentId}`이며 저장값은 enum 문자열이다. 손상되거나 모르는 값이면 미선택으로 처리한다. 브라우저 저장을 지우거나 다른 기기를 쓰면 선택이 유지되지 않는다.

컴포넌트: `src/components/student/ProfileCurrentStatus.tsx`. `ProfileScreen`의 이름 아래에 배치한다. 기존 프로필 API와 응답 형식은 그대로 유지한다.

## 서버 연동 제안 — 미구현, BE·PM 협의 필요

기존 자동 판정 상태를 그대로 덮어쓰지 않는 별도 자기 선택 상태를 제안한다.

```http
GET /api/students/{studentId}/profile/current-status
```

```json
{ "data": { "studentId": 1, "currentStatus": null, "updatedAt": null } }
```

```http
PATCH /api/students/{studentId}/profile/current-status
Content-Type: application/json

{ "currentStatus": "SEEKING" }
```

```json
{ "data": { "studentId": 1, "currentStatus": "SEEKING", "updatedAt": "2026-11-01T15:00:00+09:00" } }
```

- 제안 함수: `getProfileCurrentStatus(studentId)`, `updateProfileCurrentStatus(studentId, currentStatus)`. 이번 구현에는 네트워크 함수를 추가하지 않았다.
- 서버는 enum·수료생 존재·소유권 검증 후 저장한다. 잘못된 값은 400 INVALID_REQUEST, 수료생 없음은 404 STUDENT_NOT_FOUND, 저장 실패는 공통 오류를 반환한다. 미선택은 정상 200 null이며, 통신 실패를 미선택으로 숨기지 않는다.
- 성공 응답을 받은 뒤에만 저장 완료로 표시한다. 요청 중 중복 저장을 막고, 실패 시 기존 상태와 편집 선택을 유지한다.
- 저장 위치와 자동 판정 상태의 우선순위, 담당자에게 보여줄 값, 추천 제외·휴식 중 추천 처리, 취업 완료 후 추천·지원 유지 정책은 별도 합의가 필요하다. 이번 화면 선택만으로 추천 중단이나 지원 취소를 수행하지 않는다.
- 서버 연동 시 브라우저 미리보기 값을 서버에 자동 업로드하거나 서버 상태보다 우선하지 않는다. 서버 조회를 기준으로 전환하고 사용자 확인을 거쳐 필요한 경우 다시 저장한다.
- 기존 프로필 JSON·DB 스키마·백엔드·공용 API 문서는 수정하지 않았다.

## 검증

네 가지 선택·변경, 닫기 무저장, 새로고침 복원, 학생 ID별 분리, 다른 탭 갱신, 브라우저 저장 실패 시 상태 유지, 모바일 선택창, 기존 프로필 편집 유지. 실제 DB를 변경하지 않는다.
