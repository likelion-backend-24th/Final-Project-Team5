---
name: 'User Story'
about: '현재 Sprint에서 사용자가 확인할 결과를 정의합니다.'
title: ''
labels: 'type:story'
assignees: ''
---

## 목표와 범위

- **관련 Product Goal·사용자 시나리오·업무 규칙 — Notion 원본**: [현재 Notion 페이지 또는 Block URL]
- **최근 요구사항 Git Snapshot(보존본)**: [Repository `docs/요구사항.md` 링크 또는 아직 없음]
- **Sprint Goal**: [GitHub Project의 Sprint Goal 또는 Planning 기록]
- **완료 기준 — Notion 원본**: [현재 공통 완료 기준 Notion URL]
- **최근 완료 기준 Git Snapshot(보존본)**: [Repository `docs/Definition_of_Done.md` 링크 또는 아직 없음]
- **포함 범위**: [이번 Story에서 끝낼 결과]
- **제외 범위**: [이번 Story에서 다루지 않을 결과]

## User Story

- **As a**: [Actor]
- **I want**: [행동]
- **So that**: [사용자가 얻는 결과]

## Acceptance Criteria

- [ ] [정상 결과]
- [ ] [입력 검증·중요한 업무 규칙]
- [ ] [상태 전이·중복 요청 결과, 해당하는 경우]
- [ ] [인증·권한·소유권 실패 결과]
- [ ] [Dependency 실패와 데이터 변경 여부, 해당하는 경우]

## 경계·계약·검증

- **참여 Service**: [입력]
- **데이터 소유자**: [입력]
- **HTTP 계약**: [Git OpenAPI의 operationId·method/path 링크 또는 해당 없음]
- **Event 계약**: [Git Event Schema의 이름·Version 링크 또는 해당 없음]
- **현재 설계·Test 설명 — Notion 원본**: [현재 Notion 페이지 또는 Block URL]
- **최근 설계 Git Snapshot(보존본)**: [Repository `docs/*.md` 링크 또는 아직 없음]
- **예상 Test**: [Git의 실제 Test 이름·코드 또는 CI 링크]

## Sub-issues

- [ ] [Gateway 또는 외부 진입점 Task]
- [ ] [Service·데이터·Migration Task]
- [ ] [Contract·Acceptance Test·문서 Task]

각 항목은 GitHub의 실제 Task Sub-issue로 만들고 이 Story에 연결합니다.

## Project Field

- Status: Backlog
- Sprint: [입력]
- Priority: [P0/P1/P2]
- Size: [XS/S/M/L]
- Service: [Cross-cutting 또는 실제 Service]

## Story 종료 확인

- [ ] 필요한 Task가 실제 Sub-issue로 연결되고 완료됨
- [ ] Acceptance Criteria의 정상·주요 실패 Test가 통과함
- [ ] Sprint 통합 Test에 포함됨
- [ ] 통합 Demo와 재현 가능한 실행 Evidence가 있음
- [ ] Git의 API·Migration·OpenAPI·Test와 Notion 원본이 일치함
