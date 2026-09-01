# 🎪[FevalGo] 페스티벌 예약 관리 플랫폼

멋사 백엔드 자바 24기 심화 프로젝트 — Agile + MSA로 4주간 진행하는 페스티벌 예약 관리 플랫폼입니다.

## 진입점

- Notion (요구사항·설계 등 15종 문서 원본): [링크]
- GitHub Project (Product Backlog·Sprint 보드): [링크]
- Issue 템플릿: `.github/ISSUE_TEMPLATE`
- 문서 인덱스 (Notion 원본 링크 목록): `docs/README.md`

## 팀 구성

| 역할 | 담당자 |
|---|---|
| 회원/인증 (Auth) | 최승환 |
| 페스티벌 (Festival) | 송시훈 |
| 예약 (Reservation) | 김선우 |
| Gateway | 조민규 |

## 서비스 구성

| 서비스 | 설명 |
|---|---|
| `gateway` | API Gateway, 인증 검증·라우팅 |
| `auth-service` | 회원가입·로그인·토큰 발급 |
| `festival-service` | 페스티벌 조회·주최 신청·심사·등록 |
| `reservation-service` | 티켓 예매 신청 |
| `payment-service` | 결제·정산 |

## 실행 방법

```bash
docker compose -f infra/compose.yaml up
```

(Sprint 1 진행 중 — 실행 명령은 서비스 구성 확정 후 채웁니다.)

## 기술 스택

- **Frontend**: React (SPA)
- **Backend**: Spring Boot 3.5.15, Java 21, JPA
- **Infra**: Docker, MySQL,GitHub Actions/Jenkins
- **인증**: JWT (Access·Refresh Token)