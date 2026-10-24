# 이음 Git 브랜치 · 작업 규칙

2026-10-24 · 작성 데렌(PM)

## 브랜치 3층

```
내 브랜치 (fe-a/student-ui …)  ──PR──▶  dev  ──PM이 병합──▶  main
        매일 작업                    통합 · 테스트          최종본만
```

- `dev`: 모든 PR이 들어가는 곳. 여기서 합쳐 보고 테스트한다. 깨져도 된다.
- `main`: 동작이 확인된 최종본만. `dev`를 병합할 때만 바뀐다(10/29 얇은 연결 후, 통합 기간, 코드 프리즈).
- 팀원은 `main`을 직접 쓸 일이 없다. 항상 `dev` 기준으로 작업한다.
- **병합 담당 3명**: 김용현(PM · BE-A), 심소민(AI-A), 김하윤(FE-A). `dev`·`main` 모두 이 세 명만 PR을 병합한다. 나머지는 PR을 올리고 디스코드에 알린다.

## 규칙 5가지

1. **`main` · `dev`에 직접 push하지 않는다.** 모든 코드는 PR로만 들어가고, PR의 base는 항상 `dev`다.
2. **브랜치는 1인 1개.** 아래 표의 내 브랜치를 2주 내내 쓴다.
3. **작업 시작할 때 `dev`를 합치고, 끝낼 때 `dev`로 PR.** 병합 담당 3명(PM · 소민 · 하윤)이 확인하고 병합한다.
4. **커밋은 `종류(화면번호): 한 일`.** 종류는 `feat` · `fix` · `style` · `chore`. 예: `feat(M1): 공고 카드 3장 표시`
5. **공용 파일 · 라이브러리 추가는 PR에 표시하고 주인을 멘션한다. `.env` · 키는 올리지 않는다.**

## 내 브랜치

| 파트 | 브랜치 |
| --- | --- |
| FE-A | `fe-a/student-ui` |
| FE-B | `fe-b/resume-dashboard` |
| BE-A | `be-a/student-api` |
| BE-B | `be-b/coordinator-api` |
| AI-A | `ai-a/document` |
| AI-B | `ai-b/recommend` |

라이브러리 추가는 `chore/내용`, 통합 기간 버그는 `fix/화면번호-내용`으로 `dev`에서 짧게 따로 만듭니다. 이것도 PR은 `dev`로 올립니다.

## 매일 쓰는 명령어

```bash
# 처음 한 번
git switch dev && git pull
git switch -c fe-a/student-ui
git push -u origin fe-a/student-ui

# 작업 시작
git switch fe-a/student-ui
git pull origin dev

# 작업 중 수시로
git status          # 내 담당이 아닌 파일이 보이면 멈추고 확인
git add .
git commit -m "feat(M1): 공고 카드 3장 표시"

# 작업 끝
git pull origin dev
git push            # GitHub에서 Compare & pull request → base: dev (main 아님!)
```

PR을 만들 때 **base가 `dev`인지** 꼭 확인합니다. GitHub 기본값이 `main`으로 잡혀 있으면 드롭다운에서 `dev`로 바꿉니다. PR 본문은 템플릿 빈칸만 채웁니다. 병합된 뒤에도 브랜치는 지우지 않고 다음 작업에도 그대로 씁니다.

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
- **그 밖에 뭔가 이상하다** = `git status`와 `git branch` 결과를 캡처해 디스코드에 올립니다.
- **절대 쓰지 않는 명령어** (AI가 제안해도 실행하지 않음)
    - `git push --force` = GitHub에 있는 내용을 내 것으로 덮어씀. 남의 커밋이 사라질 수 있음.
    - `git reset --hard` = 커밋하지 않은 작업을 전부 지움. 되살릴 수 없음.

## 코드 프리즈

11/4 코드 프리즈 때 PM이 `dev`를 `main`에 병합하고 `main`에 `demo-1104` 태그를 붙입니다. 이후 수정은 PM에게 먼저 알리고, `fix/` 브랜치 → `dev` PR → 병합 담당이 확인 후 PM이 `main`에 다시 병합합니다.
