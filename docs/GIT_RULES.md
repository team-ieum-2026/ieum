# 이음 Git 브랜치 · 작업 규칙

2026-10-24 · 작성 데렌(PM)

## 규칙 5가지

1. **`main`에 직접 push하지 않는다.** 모든 코드는 PR로만 들어간다.
2. **브랜치는 1인 1개.** 아래 표의 내 브랜치를 2주 내내 쓴다.
3. **작업 시작할 때 `main`을 합치고, 끝낼 때 PR.** PM이 모아서 병합한다.
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

라이브러리 추가는 `chore/내용`, 통합 기간 버그는 `fix/화면번호-내용`으로 `main`에서 짧게 따로 만듭니다.

## 매일 쓰는 명령어

```bash
# 처음 한 번
git switch main && git pull
git switch -c fe-a/student-ui
git push -u origin fe-a/student-ui

# 작업 시작
git switch fe-a/student-ui
git pull origin main

# 작업 중 수시로
git status          # 내 담당이 아닌 파일이 보이면 멈추고 확인
git add .
git commit -m "feat(M1): 공고 카드 3장 표시"

# 작업 끝
git pull origin main
git push            # GitHub에서 Compare & pull request → base: main
```

PR 본문은 템플릿 빈칸만 채웁니다. 병합된 뒤에도 브랜치는 지우지 않고 다음 작업에도 그대로 씁니다.

## 막혔을 때

- **`CONFLICT`가 뜬다** = 나와 다른 사람이 같은 파일의 같은 줄을 다르게 고쳐서 Git이 어느 쪽을 남길지 모르는 상태.
    - VS Code에서 그 파일을 열면 충돌 부분 위에 버튼이 뜹니다. **Accept Current**(내 코드) · **Accept Incoming**(main에서 온 코드) · **Accept Both**(둘 다) 중 하나를 누르고 저장 → `git add .` → `git commit`.
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

11/4 코드 프리즈 때 PM이 `main`에 `demo-1104` 태그를 붙입니다. 이후 수정은 PM에게 먼저 알리고 PM이 병합합니다.
