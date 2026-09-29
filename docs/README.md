> Notion 원본 URL 칸은 팀 원본 페이지(루트)로 연결합니다. 문서별 하위 페이지 URL로 바꾸려면 팀 원본에서 각 페이지 링크를 복사해 교체하세요.

# 문서 원본·Git Snapshot 인덱스

이 파일은 팀 Repository의 `docs/README.md`로 사용합니다. 첫날에는 아래 15종 Notion 원본 URL과 담당자만 등록하고, 15개 Markdown 본문은 만들지 않습니다. 이 인덱스는 15종 기준 문서 수에 포함하지 않으며 Git에서 직접 관리합니다.

## 원본·Snapshot 링크 목차

| 문서 | Notion 원본 URL | 담당자 | 최근 Git Snapshot(보존본) |
|---|---|---|---|
| 요구사항 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [요구사항.md](요구사항.md) — commit `b8fe371` (2026-09-29) |
| 공통 완료 기준 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [공통완료기준.md](공통완료기준.md) — commit `b8fe371` (2026-09-29). |
| 화면 설계 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [화면설계.md](화면설계.md)  |
| 서비스 경계 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [서비스경계.md](서비스경계.md) — commit `101d079` (2026-09-28) |
| 아키텍처 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [아키텍처.md](아키텍처.md) — commit `101d079` (2026-09-28) |
| ERD | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [ERD.md](ERD.md) — commit `101d079` (2026-09-28) |
| API | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [API.md](API.md) — commit `efa88df` (2026-09-29) |
| 권한 Matrix | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [권한메트릭스.md](권한메트릭스.md) — commit `efa88df` (2026-09-29) |
| 시퀀스 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [시퀀스.md](시퀀스.md) — commit `efa88df` (2026-09-29) |
| 테스트 전략 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [테스트전략.md](테스트전략.md) — commit `cddd19f` (2026-09-29) |
| 테스트 체크리스트 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [테스트체크리스트.md](테스트체크리스트.md) — commit `cddd19f` (2026-09-29) |
| 실행·배포 가이드 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [실행·배포 가이드.md](<실행·배포 가이드.md>) — commit `cddd19f` (2026-09-29). 템플릿 파일명 `배포가이드.md`와 다름 |
| 트러블슈팅 | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [트러블슈팅.md](트러블슈팅.md) — commit `cddd19f` (2026-09-29) |
| Sprint Review | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [스프린트리뷰.md](스프린트리뷰.md) — commit `dfe407c` (2026-09-29) |
| Sprint Retrospective | [팀 Notion 원본](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 송시훈, 조민규, 최승환 | [retrospective.md](retrospective.md) — commit `dfe407c` (2026-09-29) |

최근 Git Snapshot 칸의 commit은 2026-09-29 기준 각 파일을 마지막으로 바꾼 커밋입니다. 화면 설계는 아직 `docs/`에 스냅샷이 없습니다.

## 첫날 확인

- [ ] 15종 Notion 페이지가 있고 프로젝트 검토자가 열람할 수 있습니다.
- [ ] 각 페이지 URL과 담당자가 위 표에 등록되어 있습니다.
- [ ] 팀 Repository의 `README.md`가 이 인덱스와 GitHub Project·Issue를 연결합니다.
- [ ] Git `docs/`에는 아직 15개 본문을 만들지 않았습니다.

## Sprint Review 뒤 동기화

1. Review 결과를 먼저 Notion 원본에 반영합니다.
2. 확정된 15종 현재 내용을 같은 `docs/*.md` 경로에 복사합니다.
3. 각 Snapshot 상단에 Notion 원본 URL·Snapshot 기준 시점·동기화 시각·직접 편집 금지를 기록합니다.
4. 위 표의 최근 Git Snapshot을 실제 상대 링크로 바꾸고 문서 PR을 만듭니다.
5. 수정이 필요하면 Git 파일을 직접 고치지 않고 `Notion 수정 → Git 재동기화` 순서를 지킵니다.

별도 Sprint 폴더를 만들지 않습니다. Sprint별 내용은 Git commit 이력으로 보존하고 Week 4에 최종 Notion 내용을 다시 동기화합니다. Secret·Token·Cookie·개인정보는 Notion과 Git 어디에도 기록하지 않습니다.
