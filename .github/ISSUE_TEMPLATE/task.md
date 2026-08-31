---
name: 'Task'
about: 'Story를 완성하는 Service별 기술 작업을 정의합니다.'
title: ''
labels: 'type:task'
assignees: ''
---

## Parent Story와 책임

- **Parent Story**: [일반 Task는 GitHub Story `#번호`와 실제 Parent Issue / 공통 기반 독립 기술 Issue만 `해당 없음`]
- **영향 Story·Sprint Goal**: [공통 기반 독립 기술 Issue일 때 영향 Story와 Sprint Goal, 일반 Task는 Parent Story]
- **담당 Service·책임**: [한 Service 또는 Cross-cutting]
- **목적**: [이 Task가 Parent Story의 어떤 결과를 만드는지]
- **소유 데이터**: [없음 또는 소유 Aggregate]
- **관련 사용자 시나리오·업무 규칙 — Notion 원본**: [현재 Notion 페이지 또는 Block URL]
- **최근 요구사항 Git Snapshot(보존본)**: [Repository `docs/요구사항.md` 링크 또는 아직 없음]

## 작업 범위

- [ ] [구현·설정·문서 작업 1]
- [ ] [구현·설정·문서 작업 2]
- [ ] [Migration·호환성 작업, 해당하는 경우]

## 데이터·계약 영향

- **HTTP 계약**: [Git OpenAPI의 operationId·method/path 링크 또는 해당 없음]
- **Event 계약**: [Git Event Schema의 이름·Version 링크 또는 해당 없음]
- **Migration**: [Git Migration 파일 링크 또는 해당 없음]
- **상태·실패 설명 — Notion 원본**: [현재 Notion 페이지 또는 Block URL]
- **최근 Git Snapshot(보존본)**: [Repository `docs/*.md` 링크 또는 아직 없음]

## 검증

- **실행할 Test**: [실제 Test 이름]
- **실행 명령**: [팀원이 그대로 실행할 명령]
- **확인할 결과**: [Status·상태·건수·부수 효과]
- **Evidence**: [PR·CI·Log·화면 위치, 미실행은 NOT_RUN]

## 완료 조건

- [ ] Parent Story가 연결한 Notion 공통 완료 기준 원본에 필요한 근거를 남김
- [ ] 최근 공통 완료 기준 Git Snapshot(보존본)이 있으면 Notion 원본과 불일치가 없는지 확인함
- [ ] [Unit·Component·Contract Test]
- [ ] 다른 Service DB 직접 접근 없음
- [ ] 실행 결과를 재현할 수 있음
- [ ] PR Review·통합 완료
- [ ] Parent Story의 Acceptance Criteria에 기여함

일반 Task는 정확히 하나의 Parent Story에 연결합니다. 인증·CI·배포 기반처럼 여러 Story가 공동으로 사용하는 작업만 독립 기술 Issue로 둘 수 있습니다.

현재 내용은 Notion 원본 URL을 우선합니다. Git Snapshot 링크는 가장 최근 Review 시점의 보존본이며, 내용 수정은 `Notion 수정 → Git 재동기화` 순서를 지킵니다.

## Project Field

- Status: Backlog
- Sprint: [Parent Story와 같은 Sprint]
- Priority: [P0/P1/P2]
- Size: [XS/S/M/L]
- Service: [실제 Service]
