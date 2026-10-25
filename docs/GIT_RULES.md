# 이음 Git 브랜치 · 작업 규칙

2026-10-24 · 작성 데렌(PM)

## 브랜치 3층

```
기능 브랜치 (be-a/feat/job-sync …)  ──PR──▶  dev  ──병합 담당──▶  main
   기능 하나 · 병합되면 삭제              통합 · 테스트        최종본만
```

- `dev`: 모든 PR이 들어가는 곳. 여기서 합쳐 보고 테스트한다. 깨져도 된다.
- `main`: 동작이 확인된 최종본만. `dev`를 병합할 때만 바뀐다(10/29 얇은 연결 후, 통합 기간, 코드 프리즈).
- 팀원은 `main`을 직접 쓸 일이 없다. 항상 `dev` 기준으로 작업한다.
- **병합 담당 3명**: 김용현(PM · BE-A), 심소민(AI-A), 김하윤(FE-A). `dev`·`main` 모두 이 세 명만 PR을 병합한다. 나머지는 PR을 올리고 디스코드 `깃허브-pr-리뷰` 채널에 링크를 남긴다.

## 규칙 5가지

1. **`main` · `dev`에 직접 push하지 않는다.** 모든 코드는 PR로만 들어가고, PR의 base는 항상 `dev`다.
2. **기능 하나에 브랜치 하나.** 항상 최신 `dev`에서 새로 따고, 병합되면 삭제된다(GitHub 자동 삭제).
3. **브랜치 이름은 `파트/종류/기능`.** 소문자 · 하이픈만. 아래 "브랜치 이름" 참고.
4. **커밋은 `종류(화면번호): 한 일`, PR 제목은 `종류(화면번호): 한 일 [요구사항ID]`.** 종류는 아래 "종류" 표의 6개를 주로 쓴다.
5. **공용 파일 · 라이브러리 추가는 PR에 표시하고 주인을 멘션한다. `.env` · 키는 올리지 않는다.**

병합 담당 3명(PM · 소민 · 하윤)이 확인하고 병합합니다.

## 브랜치 이름

`파트/종류/기능` — 파트는 `fe-a` `fe-b` `be-a` `be-b` `ai-a` `ai-b`, 종류는 아래 표. 소문자 · 하이픈만 씁니다.

FE와 BE가 레포를 따로 쓰는 정석 기준에서는 주로 `종류/기능`으로 쓰지만, 우리는 레포 하나에 세 파트가 있어 앞에 파트를 붙입니다.

### 종류 (주로 이 6개)

| 종류 | 언제 |
| --- | --- |
| `feat` | 새 기능 · 화면 · API · 화면 모양 개선 (CSS 포함) |
| `fix` | 버그 · 깨진 화면 수정 |
| `docs` | 문서만 수정 (README · docs/ · AGENTS.md) |
| `refactor` | 동작은 그대로, 코드 정리 |
| `build` | 라이브러리 추가 (`package.json` · `build.gradle` · `requirements.txt`) |
| `chore` | 그 밖의 설정 (`.gitignore`, 에디터 설정 등) |

이 밖에도 `style`(코드 포맷만) · `test` · `perf` · `ci` · `revert`가 있지만 P0에서는 거의 쓰지 않습니다. CSS로 화면 모양을 바꾸는 건 `style`이 아니라 `feat`/`fix`입니다.

### 예시

| 상황 | 브랜치 | PR 제목 |
| --- | --- | --- |
| M1 추천 카드 만들기 | `fe-a/feat/m1-cards` | `feat(M1): 추천 카드 3장 표시 [FE-A-03]` |
| M6 자동 저장 버그 | `fe-b/fix/m6-autosave` | `fix(M6): 자동 저장 누락 수정 [FE-B-02]` |
| 공고 배치 | `be-a/feat/job-sync` | `feat(S3): 공고 배치 동기화 [BE-A-02]` |
| 대시보드 API | `be-b/feat/dashboard` | `feat(C1): 대시보드 API [BE-B-07]` |
| 리랭커 라이브러리 추가 | `ai-a/build/add-reranker` | `build: 리랭커 라이브러리 추가` |
| 추천 코드 정리 | `ai-b/refactor/recommend` | `refactor(S3): 추천 후보 검색 함수 분리` |
| API 명세 수정 | `be-a/docs/api-spec` | `docs: API 명세 404 코드 추가` |

- 라이브러리 추가(의존성 파일 변경)는 다른 작업과 섞지 않고 `파트/build/add-라이브러리명` 브랜치 하나로 따로 올립니다.
- 기능이 커서 하루 넘게 걸리면 그 브랜치를 계속 써도 되지만, 매일 `git pull origin dev`로 최신화합니다. 이틀 넘으면 나눠서 먼저 PR합니다.

## PR 제목

`종류(화면번호): 한 일 [요구사항ID]` — PR 목록에서 누가 무엇을 했는지 바로 보이게 합니다.

| 예 |
| --- |
| `feat(S3): 공고 배치 동기화 [BE-A-02]` |
| `feat(M1): 추천 카드 3장 표시 [FE-A-03]` |
| `fix(M6): 자동 저장 누락 수정 [FE-B-02]` |
| `docs: 브랜치 규칙 변경` (문서 · 설정이라 화면 · ID가 없을 때) |

요구사항이 여러 개면 `[BE-A-04, BE-A-05]`처럼 씁니다.

## 매일 쓰는 명령어

```bash
# 기능 시작: 항상 최신 dev에서 새 브랜치
git switch dev && git pull
git switch -c be-a/feat/job-sync

# 작업 중 수시로
git status          # 내 담당이 아닌 파일이 보이면 멈추고 확인
git add .
git commit -m "feat(S3): 공고 API 1,000건씩 받아 저장"

# 작업 끝
git pull origin dev
git push -u origin be-a/feat/job-sync
# GitHub에서 Compare & pull request → base: dev → 제목 규칙대로 → 템플릿 채우기

# 병합된 뒤: dev로 돌아와 다음 기능 시작
git switch dev && git pull
git branch -d be-a/feat/job-sync     # 내 PC의 브랜치 정리 (GitHub 쪽은 자동 삭제됨)
```

PR을 만들 때 **base가 `dev`인지** 꼭 확인합니다. 기본 브랜치가 `dev`라 보통 자동으로 잡히지만, `main`이면 드롭다운에서 바꿉니다. PR 본문은 템플릿 빈칸만 채웁니다.

### 병합 담당만 하는 것: `dev` → `main`

```bash
# 얇은 연결 확인 후(10/29) · 통합 기간 · 코드 프리즈 때
# GitHub에서 PR 생성: base main ← compare dev → "Create a merge commit"
```

`main`도 보호돼 있어 병합 담당도 PR로만 넣습니다. `dev`가 동작하는 상태인지(FE · BE · AI 세 서버가 뜨고 메인 흐름이 끝까지 가는지) 확인한 뒤에만 병합합니다. `dev` → `main` 병합은 PM이 결정합니다.

## 막혔을 때

- **`CONFLICT`가 뜬다** = 나와 다른 사람이 같은 파일의 같은 줄을 다르게 고쳐서 Git이 어느 쪽을 남길지 모르는 상태.
    - VS Code에서 그 파일을 열면 충돌 부분 위에 버튼이 뜹니다. **Accept Current**(내 코드) · **Accept Incoming**(dev에서 온 코드) · **Accept Both**(둘 다) 중 하나를 누르고 저장 → `git add .` → `git commit`.
    - 남의 코드를 지우게 되면 그 사람에게 먼저 물어봅니다.
    - 모르겠으면 `git merge --abort` = 합치기 전 상태로 되돌리기. 되돌린 뒤 디스코드에 질문.
- **push가 거절된다 (`rejected`)** = GitHub에 있는 내 브랜치에 내 컴퓨터에 없는 커밋이 있는 상태. 다른 컴퓨터에서 올렸거나 GitHub 웹에서 고친 경우에 생깁니다.
    - `git pull`로 먼저 받고 다시 `git push`.
    - PR 화면의 **Update branch** 버튼은 누르지 않습니다.
- **병합된 브랜치에 실수로 계속 커밋했다** = 그 브랜치는 이미 끝났습니다. 새 브랜치로 옮깁니다.
    - `git switch -c 파트/종류/새기능` → 그대로 push → 새 PR. 옛 브랜치는 `git branch -d`로 정리.
- **브랜치가 지워졌는데 다시 필요하다** = 병합된 PR 페이지 아래 **Restore branch** 버튼.
- **그 밖에 뭔가 이상하다** = `git status`와 `git branch` 결과를 캡처해 디스코드에 올립니다.
- **절대 쓰지 않는 명령어** (AI가 제안해도 실행하지 않음)
    - `git push --force` = GitHub에 있는 내용을 내 것으로 덮어씀. 남의 커밋이 사라질 수 있음.
    - `git reset --hard` = 커밋하지 않은 작업을 전부 지움. 되살릴 수 없음.

## 코드 프리즈

11/4 코드 프리즈 때 PM이 `dev`를 `main`에 병합하고 `main`에 `demo-1104` 태그를 붙입니다. 이후 수정은 PM에게 먼저 알리고, `파트/fix/…` 브랜치 → `dev` PR → 병합 담당이 확인 후 PM이 `main`에 다시 병합합니다.
