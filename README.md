# FevalGo

> 주최자가 페스티벌을 등록·심사받아 공개하고, 참가자가 스탠딩·좌석 티켓을 예매·결제한 뒤 QR로 입장하며, 행사가 끝나면 정산까지 이어지는 MSA 기반 페스티벌 예약 관리 플랫폼입니다.

## 목차

- **소개:** [링크](#링크) · [프로젝트 개요](#프로젝트-개요) · [핵심 사용자 흐름](#핵심-사용자-흐름) · [핵심 기능](#핵심-기능) · [역할 및 권한](#역할-및-권한)
- **설계:** [기술 스택](#기술-스택) · [아키텍처](#아키텍처) · [프로젝트 구조](#프로젝트-구조) · [주요 기술 의사결정](#주요-기술-의사결정)
- **실행·검증:** [실행 방법](#실행-방법) · [테스트](#테스트) · [부하 테스트](#부하-테스트) · [서비스별 구현 현황](#서비스별-구현-현황)
- **회고:** [Troubleshooting](#troubleshooting) · [팀과 기여](#팀과-기여) · [Documentation](#documentation) · [개선 계획](#개선-계획)

## 링크

| 구분 | 링크 |
| --- | --- |
| 서비스 | https://fevalgo.duckdns.org/ |
| 데이터 모델 | [docs/ERD.md](./docs/ERD.md) — 레포에 DDL 파일은 없고, 스키마는 각 서비스 JPA 엔티티(`ddl-auto: update`)로 생성됩니다. |
| 요구사항·설계 문서 | [팀 Notion](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) |
| 발표 자료 | [최종 발표 슬라이드 (PDF)](./docs/presentation/FevalGo_final_presentation.pdf) |

## 프로젝트 개요

| 항목 | 내용 |
| --- | --- |
| 개발 기간 | 2026.08.31 ~ 2026.09.29 |
| 팀 구성 | backend 3명 (멋쟁이사자처럼 백엔드 자바 24기 심화 프로젝트) |
| 주요 사용자 | 페스티벌 참가자(관람객), 주최자, 주최자가 초대한 현장 입장 도우미, 부스 운영자, 플랫폼 운영자 |
| 해결하려는 문제 | 페스티벌 주최자는 등록·심사·판매·정산을, 참가자는 예매·결제·현장 입장을 각기 다른 채널이나 수작업으로 처리해서 한 흐름으로 이어지지 않습니다. |
| 핵심 가치 | 참가자(예매 → 결제 → QR 입장), 주최자(신청 → 등록 → 도우미 운영 → 정산), 운영자(심사 → 취소 승인 → 정산 확정)를 하나의 서비스에서 이어 주고, 결제·환불·정산은 재조회와 멱등 처리로 정합성을 지킵니다. |

## 핵심 사용자 흐름

```text
[참가자]
회원가입(이메일 인증) / 카카오·구글 로그인
  → 페스티벌 탐색(목록·상세·지도, AI 추천 챗봇)
  → 티켓 선택: 스탠딩(수량) 또는 좌석(구역 선택 → 실시간 좌석맵)
  → 예매 생성(10분 홀드) → PortOne 결제(카드 / 무통장입금)
  → 마이페이지에서 QR·입장 코드 확인 → 현장 입장(주최자·도우미가 검증)
  → (필요 시) 환불 견적 확인 → 부분·전체 환불
  → 부스 대기 신청 → 내 차례 알림(챗봇 위젯 폴링)

[주최자]
주최자 신청 → 운영자 승인(USER → HOST)
  → 페스티벌 등록(이미지·티켓·좌석 배치, AI 등록 초안) → 운영자 심사
  → 공개(PUBLISHED, 좌석 생성) → 판매 → 도우미 초대 → 현장 입장 검증
  → 종료(CLOSED) → 정산 조회
  └ 행사 취소 요청 → 운영자 승인 → 결제 일괄 전액 환불 → 취소 완료(CANCELLED)

[부스 운영자]  부스 개설(WAITING) → 오픈(OPEN) → 대기열 현황 확인·다음 번호 호출
[운영자]      회원 조회·정지 / 주최 신청 심사 / 페스티벌 심사·운영 현황 / 취소 승인 / 정산 확정·지급 기록
```

## 서비스별 구현 현황

| 서비스 | 역할 | 구현 상태 | 판정 근거 |
| --- | --- | --- | --- |
| gateway | 라우팅, JWT 검증, 신원 헤더 전달, HELPER 허용 목록·세션 검증 | ✅ 구현 | `JwtAuthenticationGlobalFilter`, `HelperSessionClient`, `TraceIdGlobalFilter` |
| auth-service | 이메일 인증 가입·로그인·토큰, 카카오·구글 로그인, 내 정보, 운영자 회원·주최자 관리, 역할 부여 내부 API, 도우미 계정 | ✅ 구현 / ⏳ STOREHOST 부여 API 없음(DB에서 직접 부여) | `AuthService`, `TokenSessionService`, `UserService`, `AdminUserService`, `RoleService`, `HelperAccountService`, `Role` |
| festival-service | 페스티벌 조회·등록·심사·종료, 주최 신청, 행사 취소, 티켓 재고, 부스, AI 챗봇·등록 초안, 좌표 보정 | ✅ 구현 / ⏳ 페스티벌 수정·삭제 API 미구현 | `FestivalService`, `HostApplicationService`, `FestivalCancellationService`, `TicketTypeService`, `BoothService`, `ChatbotService`, `FestivalAiDraftService` |
| reservation-service | 스탠딩·좌석 예매, 좌석맵·실시간 좌석 상태, 결제 확정·만료, 입장 검증, 환불 견적·반영, 부스 대기열 | ✅ 구현 / ⏳ 주최자별 구매 한도 설정 미구현 | `ReservationService`, `SeatGenerationService`, `SeatBroadcastService`, `RefundPolicy`, `BoothWaitlistService` |
| payment-service | PortOne 결제 준비·검증, 웹훅, 환불, 보상 환불, 행사 취소 일괄 환불, 정산 | ✅ 구현(가상계좌 입금은 테스트 채널용 데모 자동 입금) / ⏳ 빌링키(정기결제)·실제 송금 자동화 미구현 | `PaymentService`, `WebhookEventService`, `PaymentCancellationService`, `FestivalRefundScheduler`, `SettlementService` |

## 핵심 기능

### 회원 인증 · 계정 ✅

- 이메일 인증코드를 확인한 뒤 가입하고, 일반 로그인 또는 카카오·구글 로그인으로 접속합니다. 구글 로그인에서 같은 이메일의 일반 계정이 있으면 연동 동의를 거쳐 하나의 계정으로 연결합니다. 닉네임·비밀번호 변경, 비밀번호 재설정, 탈퇴를 지원합니다.
- Access Token(1시간)은 응답 본문으로, Refresh Token(14일)은 `HttpOnly`·`Secure`·`SameSite=Strict` 쿠키로 발급합니다. Refresh Token은 SHA-256 해시로만 저장하고 재발급 때마다 교체(rotation)하며, 폐기된 토큰이 다시 쓰이면 재사용으로 판단해 전체 세션을 폐기합니다(5초 유예, 아래 의사결정 참고). 비밀번호 5회 실패 시 10분간 잠그고, 실패 횟수는 `LoginAttemptService`가 `REQUIRES_NEW` 트랜잭션과 비관적 락으로 기록합니다. 인증코드는 6자리·5분 유효, 재발송 30초 쿨다운·10분당 5회 제한, 5회 오답 시 잠금입니다. 탈퇴는 행을 남긴 채 이름·닉네임·이메일을 익명화하고 소셜 연결을 삭제합니다.

### 주최자 신청 · 심사 ✅

- 일반 회원이 소개·연락처를 적어 주최자 신청을 하고, 운영자가 승인하면 HOST가 됩니다. 반려 시 사유를 남기고 재신청할 수 있으며, 신청 이력을 조회할 수 있습니다.
- 승인은 `HostApplicationService`가 신청을 `APPROVAL_PENDING`으로 먼저 저장한 뒤 auth-service 내부 API(`PUT /internal/v1/roles`)로 역할을 부여하고, 성공하면 `APPROVED`로 확정합니다. 응답을 받지 못하면 `202 Accepted`로 대기 상태를 알리고 `HostApplicationApprovalRetryScheduler`가 재시도합니다(아래 의사결정 참고).

### 페스티벌 등록 · 조회 · 심사 ✅ (수정·삭제 ⏳)

- 주최자는 이미지(썸네일 1장·상세 2장, 장당 10MB), 기간·운영 시간, 장소·좌표, 스탠딩/좌석 티켓(좌석은 구역·좌석 배치)을 입력해 등록하고, 소개 문구에서 AI 초안을 받을 수 있습니다. 운영자가 공개·반려를 심사하며, 공개된 페스티벌만 목록에 노출됩니다. 등록 후 수정·삭제 API는 아직 없습니다.
- 공개 시 좌석 티켓마다 reservation-service에 좌석 생성을 요청하고(`PUBLISH_PENDING` → `PUBLISHED`, 실패 시 `FestivalPublishRetryScheduler` 재시도), 종료 시각이 지난 페스티벌은 `FestivalExpiryScheduler`가 `CLOSED`로 바꿉니다. 업로드 이미지는 확장자뿐 아니라 실제 디코딩으로 검증하고(`FestivalImageUploadService`), 조회수는 IP를 SHA-256으로 해시해 24시간에 1회만 집계합니다(`FestivalViewService`). 재고 차감은 "남은 수량 ≥ 요청 수량이며 공개 상태"를 조건으로 한 원자적 UPDATE입니다(`TicketTypeRepository`).

### 예매 · 좌석 ✅

- 스탠딩은 수량을, 좌석은 좌석맵에서 좌석을 골라 예매합니다. 예매는 10분간 `PENDING`으로 홀드되며, 결제 전 취소할 수 있습니다. 1인당 구매 한도는 페스티벌 단위 합산(기본 4장)입니다.
- `ReservationService`가 (사용자, 페스티벌) 잠금 행(`PurchaseLimitLock`)으로 같은 사용자의 동시 요청을 직렬화한 뒤 보유 수량을 합산합니다. 좌석은 `AVAILABLE → HELD` 조건부 UPDATE로 선점하고, 상태 변화는 STOMP(`/ws`, `/topic/...`)로 좌석맵에 브로드캐스트합니다(`SeatBroadcastService`). 만료된 홀드는 `ReservationExpiryScheduler`가 60초마다 정리하며 재고 복구에 실패하면 대기열로 재시도합니다.

### 결제 · 환불 ✅

- PortOne 결제창에서 카드(카카오페이 포함)·무통장입금(가상계좌)으로 결제합니다. 환불은 위약금 견적을 확인한 뒤 일부 장수 또는 전체를 환불합니다.
- 결제 완료는 브라우저 결과가 아니라 PortOne 단건 재조회 결과로 확정하고, 완료 API와 웹훅이 같은 동기화 로직(`PaymentService#syncPayment`)을 씁니다. 웹훅은 서명 검증 후 `webhook_id`로 중복을 막고, 일시 장애는 `WebhookRetryScheduler`가 최대 30회 재시도합니다. 환불 금액은 공연 일정을 아는 reservation-service가 견적(`RefundPolicy`: 공연 24시간 전 마감, 남은 일수별 위약금 0~30%)을 내고 payment-service가 집행하며, `Idempotency-Key`로 중복 환불을 막습니다. 환불된 재고·좌석은 즉시 풀지 않고 매일 19시에 일괄 반환합니다(`StockReleaseScheduler`, `SeatReleaseScheduler`). 가상계좌는 테스트 채널 전제로 발급 후 일정 시간이 지나면 입금된 것으로 처리하는 데모 모드가 있습니다(`VirtualAccountDemoDepositScheduler`).

### 행사 취소 · 일괄 환불 ✅

- 주최자는 공개 중이고 시작 전인 행사에 한해 사유와 함께 취소를 요청하고, 운영자가 승인·반려합니다. 승인되면 해당 행사의 결제가 위약금 없이 전액 환불된 뒤 행사가 `CANCELLED`가 됩니다.
- `FestivalCancellationService`가 `CANCELLATION_PENDING` 상태와 이전 상태를 관리하고, payment-service의 `FestivalRefundScheduler`가 환불 대상 조회 → 결제별 환불 → 모든 환불 반영 후 취소 완료 호출 순으로 처리합니다. 취소가 진행 중인 행사는 참가자 본인 환불(위약금 적용)을 막습니다.

### 입장(QR) · 도우미 ✅

- 결제가 확정되면 QR과 입장 코드(`XX-XXXX-XXXX`)가 발급되고, 주최자 또는 도우미가 카메라로 QR을 스캔하거나 코드를 입력해 입장 처리합니다. 주최자는 이메일로 도우미를 초대하며, 도우미는 초대 링크에서 비밀번호를 정해 활성화합니다.
- 입장은 조건부 UPDATE로 동시 스캔에도 한 번만 처리되고, 공연 시작 전·다른 공연 티켓·미확정 예매는 거절합니다. 도우미 계정(`HelperAccountService`)은 초대 토큰을 해시로 저장하고 만료를 행사 종료 이전으로 제한하며, 해지 시 세션 버전을 올려 게이트웨이가 다음 요청부터 차단합니다. 행사 종료 후 설정된 시간(기본 24시간)이 지나면 `HelperAccountExpiryScheduler`가 계정을 삭제합니다.

### 부스 · 대기열 ✅

- 부스 운영자(STOREHOST)는 페스티벌에 부스를 개설하고(`WAITING`), 오픈하면 공개됩니다. 해당 페스티벌 티켓을 가진 참가자는 대기 신청으로 번호를 받고, 운영자가 다음 번호를 호출하면 15초 주기 폴링으로 "내 차례"를 알립니다.
- 부스는 페스티벌당 1개(`booths.festival_id` unique)이며, 대기 번호는 카운터 행의 원자적 UPDATE로 발급합니다(`BoothWaitlistService`). STOREHOST 역할을 부여하는 API는 없어 DB에서 직접 부여합니다(실행 방법 참고).

### 정산 ✅ (실제 송금 자동화 ⏳)

- 행사 종료 24시간 후 정산을 계산하고, 운영자가 확정·지급 기록·보류·재계산을 처리합니다. 주최자는 본인 정산만 조회합니다. 지급은 송금 확인번호를 수동으로 기록하는 방식이며 실제 송금 자동화는 없습니다.
- `SettlementScheduler`가 매일 02:00(Asia/Seoul) 정산 후보를 계산하며, PG·예매·결제 기록이 맞지 않으면 금액을 추정하지 않고 보류합니다. 수수료는 결제수단별(카드·간편결제 7.5%, 가상계좌 5%)이고, 운영자 명령은 `Idempotency-Key` 필수와 감사 로그(`SettlementAuditLog`), 낙관적 락(`@Version`)으로 중복·동시 확정을 막습니다.

### 관리자 ✅

- 운영자 대시보드에서 회원 검색·정지·해제, 주최자 목록, 주최 신청 심사, 페스티벌 심사·운영 현황·요약 수치, 행사 취소 승인, 정산을 관리합니다. 정지 시 해당 회원의 Refresh Token을 모두 폐기합니다.
- 역할 검사는 각 서비스 계층(`AdminUserService`, `AdminFestivalController` → `FestivalService` 등)에서 `ADMIN` 여부를 확인합니다.

### AI 기능 ✅

- 로그인한 사용자는 챗봇에게 원하는 페스티벌을 설명해 추천(최대 3개)을 받고, 주최자는 짧은 설명으로 페스티벌 소개·티켓 구성 초안을 받습니다.
- Gemini REST API를 직접 호출하고 `responseSchema`로 JSON 응답을 강제합니다. 챗봇은 공개 중이면서 아직 끝나지 않은 페스티벌만 후보로 넘기고 후보에 없는 ID는 버리며, 대화 이력은 최근 10개만 사용하고 서버에 저장하지 않습니다(`ChatbotService`, `FestivalAiDraftService`). 챗봇 경로는 게이트웨이 공개 경로에서 제외해 로그인을 요구합니다.

## 역할 및 권한

### 역할 정의

| 역할 | 설명 | 부여 방식 |
| --- | --- | --- |
| `USER` | 일반 회원(참가자). 가입 시 기본값 | 회원가입 |
| `HOST` | 페스티벌 주최자 | 주최자 신청 → 운영자 승인 |
| `HELPER` | 주최자가 초대한 현장 입장 도우미. 특정 페스티벌 1개에 묶인 임시 계정 | 주최자의 이메일 초대(auth-service 내부 API로 비활성 계정 생성) → 초대 수락 시 활성화 |
| `ADMIN` | 플랫폼 운영자 | DB에서 직접 부여(전용 API·시드 없음) |
| `STOREHOST` | 부스 운영자 | DB에서 직접 부여(전용 API·시드 없음) |

역할 값은 auth-service `Role` enum에 정의되며, JWT `role` claim과 각 서비스의 역할 비교에서 같은 대문자 문자열을 씁니다.

### 엔드포인트 × 역할 권한

✔ 허용 · ✖ 거부 · ○ 조건부 · (GW) 게이트웨이 단계에서 차단

| 기능 | 비로그인 | USER | HOST | HELPER | ADMIN | STOREHOST |
| --- | --- | --- | --- | --- | --- | --- |
| 회원가입·로그인·재발급·이메일 인증·소셜 로그인·도우미 초대 수락 `/api/auth/**` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| 페스티벌·부스 조회, 좌석맵 `/api/festivals/**`, `/api/booths/**` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| 내 정보 조회 `GET /api/users/me` | ✖ | ✔ | ✔ | ✔ | ✔ | ✔ |
| 닉네임·비밀번호·프로필·탈퇴 `/api/users/me/**` | ✖ | ✔ | ✔ | ✖(GW) | ✔ | ✔ |
| 주최자 신청 `POST /api/host-applications` | ✖ | ✔ | ✖(409) | ✖(GW) | ✖(403) | ✔ |
| 예매·결제·환불 `/api/reservations/**`, `/api/payments/**` | ✖ | ✔ | ✔ | ✖(GW) | ✔ | ✔ |
| 부스 대기 신청 `/api/booth-waitlists/{boothId}` | ✖ | ○ 티켓 보유 | ○ 티켓 보유 | ✖(GW) | ○ 티켓 보유 | ○ 티켓 보유 |
| AI 챗봇 `/api/chatbot/**` | ✖ | ✔ | ✔ | ✖(GW) | ✔ | ✔ |
| 페스티벌 등록·관리·AI 초안·취소 요청·도우미 관리 `/api/host/festivals/**` | ✖ | ✖ | ○ 본인 행사 | ✖(GW) | ✖ | ✖ |
| 주최자 정산 조회 `/api/host/settlements/**` | ✖ | ✖ | ○ 본인 | ✖(GW) | ✖ | ✖ |
| 입장 검증·현황 `/api/organizer/reservations/verify`, `/verify-code`, `/check-in-stats` | ✖ | ✖ | ○ 본인 행사 | ○ 배정 행사 | ✖ | ✖ |
| 부스 개설·관리 `/api/store/booths/**`, 대기열 호출 `/api/booth-waitlists/booths/**` | ✖ | ✖ | ✖ | ✖(GW) | ✖ | ○ 본인 부스 |
| 운영자 기능 `/api/admin/**`(회원·주최자·심사·운영·취소·정산) | ✖ | ✖ | ✖ | ✖(GW) | ✔ | ✖ |
| PortOne 웹훅 `/api/v1/webhooks/**` | PortOne 서명으로 인증 | | | | | |

- 예매·결제는 로그인한 사용자 누구나 할 수 있도록 의도했고, 입장 검증 전용인 HELPER만 게이트웨이의 "기본 차단 + 허용 목록"(`GET /api/users/me`, 입장 검증 3종)으로 제한합니다. 그 외 역할 검사는 각 서비스 계층의 문자열 비교입니다. 행위·조건별 상세 판정은 [docs/권한메트릭스.md](./docs/권한메트릭스.md)를 참고하세요.

### 역할 전환 흐름 (USER → HOST)

신청 → 운영자 승인 → auth-service 역할 부여 → 실패 시 재시도로 이어지는 전환 시퀀스는 [docs/시퀀스.md](./docs/시퀀스.md)의 "Story 4 — 주최 신청 심사·권한 부여"를 참고하세요.

## 기술 스택

| 영역 | 기술(버전) | 선택 이유 |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5.15, Spring Data JPA, Spring Security, Bean Validation | 4개 서비스를 루트 `subprojects` 공통 설정으로 같은 스택에 맞추고, JPA로 엔티티 기반 영속성을, Bean Validation으로 요청 DTO 검증을, Spring Security로 무상태(STATELESS) 설정과 BCrypt 비밀번호 해싱을 처리하기 위해 |
| Gateway | Spring Cloud Gateway(WebFlux) — Spring Cloud 2025.0.0 | 단일 진입점에서 JWT를 검증하고 사용자 정보를 헤더로 전달해 서비스별 인증 중복을 없애고, HELPER 허용 경로를 한곳에서 통제하기 위해 |
| 인증 | JWT(jjwt 0.12.6), 카카오·구글 OAuth(Authorization Code, 백엔드 콜백) | 서버 세션 없이 게이트웨이가 서명만으로 사용자를 확인하고, 카카오·구글 계정 로그인을 백엔드 콜백에서 처리하기 위해 |
| 데이터 | MySQL 8.0(단일 인스턴스·서비스별 스키마), 테스트는 H2(MySQL 모드) | 서비스별 스키마·계정을 분리해 각 서비스가 자기 데이터만 다루게 하면서 인스턴스는 core-db 하나로 운영하고, 테스트는 H2 인메모리 DB로 외부 DB 없이 실행하기 위해 |
| 결제 | PortOne V2 — 서버 SDK `io.portone:server-sdk` 0.12.0(웹훅 서명 검증), REST 직접 호출(조회·취소), 브라우저 SDK `@portone/browser-sdk` ^0.1.9 | 카드(간편결제 포함)·가상계좌 결제를 한 연동으로 처리하고, 웹훅 서명 검증과 결제 재조회로 결제 결과를 서버에서 확정하기 위해 |
| 실시간 | Spring WebSocket(STOMP, simple broker), `@stomp/stompjs` ^7.3.0 | 좌석 선점·확정 상태를 좌석맵에 실시간 반영하고, 별도 메시지 브로커 없이 내장 simple broker로 처리하기 위해 |
| AI | Google Gemini API(REST 직접 호출) | 페스티벌 추천 챗봇과 페스티벌 등록 초안을 생성하기 위해(`responseSchema`로 JSON 응답 형식 고정) |
| 지도·좌표 | Kakao Maps JS SDK(프론트), Kakao Local API(좌표 보정) | 페스티벌 위치를 지도로 보여 주고, 장소 검색으로 좌표가 없는 페스티벌의 좌표를 보정하기 위해 |
| 메일 | Spring Mail(Gmail SMTP) | 회원가입·비밀번호 재설정용 이메일 인증코드와 도우미 초대 메일을 발송하기 위해 |
| Frontend | React ^19.2.8, React Router ^7.18.3, Vite ^8.2.2, Tailwind CSS ^4.3.3, Axios ^1.20.0, jsQR ^1.4.0, react-qr-code 2.2.0, oxlint ^1.79.0(Lint) | 역할별 라우트 SPA, QR 스캔·표시 |
| Test | JUnit 5, Spring Boot Test, Mockito / Vitest ^5.0.0, Testing Library, jsdom | 백엔드는 H2 기반 인수 테스트와 Mockito 단위 테스트로 검증하기 위해 |
| Build | Gradle 9.7.1(Wrapper), npm | Gradle 루트(`backend/`)에서 5개 모듈을 함께 빌드·테스트하기 위해 |
| Infra · CI/CD | Docker, Docker Compose, Nginx(리버스 프록시·TLS), GitHub Actions, GHCR | Compose로 DB·서비스·프록시를 함께 구성하고, Nginx로 HTTPS와 `/api`·`/ws`·정적 파일 라우팅을 처리하며, 저사양 서버에서의 이미지 빌드 부담을 피하려고 GitHub Actions 러너에서 빌드해 GHCR에 올리고 서버는 pull만 하기 위해 |

## 아키텍처

### MSA 구조도

```text
Browser (React SPA)
   │ HTTPS
   ▼
Nginx ─ /                → frontend (nginx, 정적 SPA)
      ─ /api/ , /ws      → gateway :8080 (Spring Cloud Gateway: JWT 검증·라우팅)
                              │
   ┌──────────────────────────┼───────────────────────────┬───────────────────────────┐
   ▼                          ▼                           ▼                           ▼
auth-service :8081      festival-service :8082     reservation-service :8083    payment-service :8084
/api/auth/**            /api/festivals/**          /api/festivals/*/            /api/payments/**
/api/users/**           /api/host-applications/**    ticket-types/*/seats       /api/admin/settlements/**
/api/admin/users/**     /api/host/festivals/**     /api/reservations/**         /api/host/settlements/**
/api/admin/hosts/**     /api/admin/festivals/**    /api/organizer/              /api/v1/webhooks/**
                        /api/admin/host-applications/**  reservations/**
                        /api/booths/**             /api/booth-waitlists/**
                        /api/store/booths/**       /ws (STOMP)
                        /api/chatbot/**
   │                          │                           │                           │
   └──────────────┬───────────┴───────────────┬───────────┴───────────────────────────┘
                  ▼                           ▼
     core-db (MySQL 8.0 단일 인스턴스)      서비스 간 내부 API(/internal/v1/**, 동기 HTTP)
     auth_db · festival_db · reservation_db · payment_db (서비스별 스키마·계정)

외부 연동
  auth-service      → Gmail SMTP(인증·초대 메일), Kakao/Google OAuth
  festival-service  → Gemini API(챗봇·등록 초안), Kakao Local API(좌표 보정)
  payment-service   → PortOne V2 REST(결제 조회·취소) / PortOne → 웹훅 수신
  Browser           → PortOne 브라우저 SDK(결제창), Kakao Maps JS SDK
```

- 게이트웨이 라우트는 선언 순서대로 매칭되므로, 좌석맵 경로(`/api/festivals/*/ticket-types/*/seats` → reservation)를 페스티벌 경로보다 먼저 선언합니다.
- 서비스 탐색(Eureka 등)은 쓰지 않고, 각 서비스 주소를 환경 변수(`*_SERVICE_URL`)로 지정합니다.

### 인증·요청 흐름

```text
① 로그인  POST /api/auth/login ──▶ gateway(공개 경로) ──▶ auth-service
          ◀── 본문: accessToken(1시간) / Set-Cookie: refreshToken(14일, HttpOnly·Secure·SameSite=Strict)

② 요청    클라이언트가 Authorization: Bearer {accessToken} 부착

③ gateway JwtAuthenticationGlobalFilter
          - 클라이언트가 보낸 X-User-Id / X-User-Role / X-Festival-Id / X-Token-Iat 헤더를 항상 제거
          - 공개 경로면 그대로 통과, 아니면 서명·만료 검증 (실패 시 401 + WWW-Authenticate: Bearer)
          - 검증 통과 시 X-User-Id, X-User-Role, X-Token-Iat(+ HELPER는 X-Festival-Id) 부착
          - HELPER: 허용 목록 외 경로는 403
                    허용 경로도 HelperSessionClient가 auth-service 세션 확인
                    (GET /internal/v1/helper-accounts/session, 3초 타임아웃, 실패·타임아웃 시 401)

④ 서비스  @RequestHeader("X-User-Id"/"X-User-Role")로 사용자를 식별하고, 역할은 서비스 계층에서 검사
          (헤더가 없으면 401 UNAUTHORIZED)
```

- 비밀번호를 변경·재설정하면 Refresh Token이 모두 폐기되고 `GET /api/users/me`는 `X-Token-Iat`이 변경 시각보다 이전인 토큰을 거부합니다. HELPER 토큰에는 `festivalId`와 `helperSessionVersion` claim이 들어가며, 해지·재활성화 시 세션 버전이 올라가 이전 토큰은 게이트웨이 세션 확인에서 막힙니다.

> 공통 응답 포맷(`ApiResponse`)·에러 코드·페이징 `meta` 규칙은 [docs/API.md의 공통 규약](./docs/API.md#공통-규약)을 참고하세요.

### 서비스 간 통신

- 모든 서비스 간 호출은 **동기 HTTP**입니다. 서비스는 Spring `RestClient`, 게이트웨이는 `WebClient`를 쓰며, 메시지 큐·이벤트 브로커는 사용하지 않습니다.
- 내부 API는 `/internal/v1/**` 경로이며 게이트웨이에 라우트가 없습니다. 호출 시 `Authorization: Bearer {내부 토큰}`을 붙이고, 토큰은 `INTERNAL_AUTH_TOKEN`(대부분)과 `INTERNAL_RESERVATION_TOKEN`(festival → reservation 좌석 생성) 두 종류입니다.
- 호출자 → 대상별 내부 API 전체 목록은 [docs/API.md의 서비스 간 동기 계약](./docs/API.md#서비스-간-동기-계약)을 참고하세요.
- **장애 시 패턴**: 분산 트랜잭션 대신 "중간 상태를 먼저 남기고 → 외부 호출 → 성공 시 확정, 실패 시 스케줄러가 재시도"하는 방식을 씁니다. 재시도 대상 호출은 모두 멱등하게 설계했습니다.

| 중간 상태·대기열 | 재시도 주체 | 주기 |
| --- | --- | --- |
| 주최 신청 `APPROVAL_PENDING` | `HostApplicationApprovalRetryScheduler` | 60초(30초 경과 건) |
| 페스티벌 `PUBLISH_PENDING` | `FestivalPublishRetryScheduler` | 60초(30초 경과 건) |
| 예매 만료·환불 재고 반환 대기열 | `StockReleaseScheduler`, `SeatReleaseScheduler` | 60초 |
| 실패한 웹훅(`FAILED`) | `WebhookRetryScheduler` | 60초, 최대 30회 |
| 예매 확정이 거절된 결제의 보상 환불 | `PaymentCompensationScheduler` | 60초 |
| 행사 취소 일괄 환불 | `FestivalRefundScheduler` | 60초 |

- 조회를 보조하는 호출(신청자 정보, 예상 환불액 등)은 실패해도 빈 값으로 응답을 이어갑니다.

### Gradle 멀티모듈 구성

- Gradle 루트는 레포 루트가 아니라 **`backend/`** 입니다. `backend/settings.gradle`이 5개 모듈을, 루트 `build.gradle`의 `subprojects {}`가 공통 설정(Spring Boot 3.5.15, Java 21 toolchain 등)을 맡고, 프론트엔드는 별도 npm 프로젝트입니다.
- **공통 모듈은 없습니다.** 각 서비스가 `ApiResponse`, `ErrorCode`, `ApiException`, `GlobalExceptionHandler`, `SecurityConfig` 등을 각자 가지고 있어 독립적으로 빌드·배포됩니다.

## 프로젝트 구조

```text
fevalGo/
├── backend/                      # Gradle 루트 (5개 모듈)
│   ├── gateway/                  # 라우팅·JWT 검증·HELPER 허용 목록 (filter/)
│   ├── auth-service/             # org.example.authservice
│   │   ├── auth/                 #   가입·로그인·토큰·OAuth·이메일 인증
│   │   ├── user/                 #   내 정보·탈퇴, 사용자 조회 내부 API
│   │   ├── admin/                #   운영자 회원·주최자 관리
│   │   ├── role/                 #   역할 부여 내부 API(멱등)
│   │   ├── helper/               #   도우미 초대·활성화·해지·만료 삭제
│   │   └── common/               #   ApiResponse·예외 처리
│   ├── festival-service/         # org.example.festivalservice
│   │   ├── controller/           #   공개·주최자·운영자·부스·챗봇·내부 API
│   │   ├── domain/               #   festival, tickettype, hostapplication, booth, chatbot, helper
│   │   ├── infrastructure/       #   gemini, kakao, reservation, payment 클라이언트
│   │   └── common/
│   ├── reservation-service/      # org.example.reservationservice
│   │   ├── reservation/          #   예매·입장·환불(entity/refund)·만료 스케줄러·festival 클라이언트
│   │   ├── seat/                 #   좌석 생성·좌석맵·WebSocket 브로드캐스트
│   │   ├── boothwaitlist/        #   부스 대기열
│   │   └── common/
│   ├── payment-service/          # org.example.paymentservice
│   │   ├── domain/               #   payment, cancellation, webhook, settlement
│   │   ├── infrastructure/       #   portone, reservation 클라이언트
│   │   └── common/
│   ├── build.gradle · settings.gradle · gradlew
├── frontend/                     # React + Vite SPA (npm)
├── mysql-init/core-db/           # festival_db·reservation_db·payment_db 스키마·계정 생성 스크립트
├── nginx/nginx.conf              # 리버스 프록시(HTTPS, /api·/ws → gateway, / → frontend)
├── docker-compose.yml            # core-db + 5개 백엔드 + frontend + nginx
├── docker-compose.small.yml      # 1GB RAM 서버용 JVM 힙·MySQL 튜닝 override
├── .env.example                  # 백엔드·compose 환경 변수 키 목록
└── .github/                      # 이슈·PR 템플릿, CI/CD 워크플로
```

## 실행 방법

### 요구 사항

- JDK 21 (Gradle toolchain 기준)
- Node.js 22 이상 (CI는 24, 프론트 Docker 빌드는 22 사용)
- Docker / Docker Compose (MySQL 실행용, 전체 컨테이너 실행 시)

### 환경 변수

루트 `.env.example`을 복사해 루트 `.env`를 만들고(`docker-compose.yml`의 `env_file: .env`), 프론트는 `frontend/.env.example`을 참고해 `frontend/.env`를 만듭니다. 각 서비스 `application.yaml`의 모든 환경 변수 참조에는 로컬 기본값이 있어, 외부 연동 키가 없으면 해당 기능만 동작하지 않습니다. 실제 값은 각자 발급받아 채우고 커밋하지 않습니다.

| 외부 키가 있어야 동작하는 기능 | 키 이름 |
| --- | --- |
| 결제 (PortOne) | `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY_PAYMENT`, `PORTONE_API_SECRET`, `PORTONE_WEBHOOK_SECRET` |
| AI 챗봇·등록 초안 (Gemini) | `GEMINI_API_KEY` |
| 인증·초대 메일 (Gmail SMTP) | `MAIL_USERNAME`, `MAIL_APP_PASSWORD` |
| 소셜 로그인·지도 (Kakao·Google) | `KAKAO_CLIENT_*`, `GOOGLE_CLIENT_*`, `KAKAO_MAP_REST_API_KEY`, 프론트 `VITE_KAKAO_MAP_APP_KEY` |

- 배포용 `docker-compose.yml`은 `REQUIRE_CONFIGURED_SECRETS=true`로 서비스를 띄워, `JWT_SECRET`·`INTERNAL_AUTH_TOKEN`·`INTERNAL_RESERVATION_TOKEN`·`PORTONE_API_SECRET`·`PORTONE_WEBHOOK_SECRET` 중 그 서비스가 쓰는 값이 비어 있거나 `.env.example`의 기본값이면 환경 변수 이름만 출력하고 기동을 멈춥니다. 아래 로컬 재현 구성(`docker-compose.local.yml`)은 이 검사를 끕니다.
- `festival_db`·`reservation_db`·`payment_db`의 계정을 바꾸면 `mysql-init/core-db/*.sql`도 함께 맞춰야 합니다.
- 전체 환경 변수 목록은 [docs/실행·배포 가이드.md](./docs/실행·배포%20가이드.md)를 참고하세요.

### 새 PC에서 전체 스택 재현 (docker compose 한 번)

인증서·도메인 없이 HTTP로 전체 스택(core-db, 5개 백엔드, frontend, nginx)을 띄우고, 백엔드 jar는 이미지 안에서 빌드합니다. Docker Compose 2.24 이상이 필요합니다.

```bash
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
# http://localhost:8088
```

- 프로젝트 이름(`fevalgo-local`)과 컨테이너 이름을 따로 써서 같은 PC의 다른 스택과 겹치지 않고, 호스트에는 8088 포트만 엽니다.
- auth-service가 로컬 전용 시드 계정 3개(`local-admin@fevalgo.test`, `local-storehost@fevalgo.test`, `local-user@fevalgo.test`)를 만듭니다. 비밀번호는 `.env`의 `LOCAL_SEED_PASSWORD`입니다.
- PortOne 결제 완료·메일 발송·소셜 로그인·카카오맵은 실제 키가 없으면 동작하지 않습니다(카카오맵 키는 운영 도메인만 허용).
- 2026-09-29 새 clone에서 8개 컨테이너 기동과 API 스모크 17단계(시드 계정 로그인 → 주최 신청·승인 → 등록·공개 → 부스 → 예매 → 결제 준비 → 401·403·409 실패 경로)를 확인했습니다.

### 로컬 개발 실행

```bash
# 1) 레포 루트에서 MySQL만 컨테이너로 실행 (localhost:3307)
docker compose up -d core-db

# 2) 백엔드 — backend/ 에서 각각 별도 터미널로 실행
./gradlew :auth-service:bootRun          # 8081
./gradlew :festival-service:bootRun      # 8082
./gradlew :reservation-service:bootRun   # 8083
./gradlew :payment-service:bootRun       # 8084
./gradlew :gateway:bootRun               # 8080

# 3) 프론트엔드 — frontend/ 에서 실행
npm ci && npm run dev                    # http://localhost:5173 (게이트웨이 CORS·WebSocket 허용 origin이라 포트 유지)
```

- 서비스 간 기동 순서 의존은 없습니다(기동 시 서로 호출하지 않으며, 기동 순서로 인한 호출 실패는 재시도 스케줄러가 흡수합니다). 요청을 받을 게이트웨이는 마지막에 띄우는 것을 권장합니다.
- 각 서비스는 `spring-dotenv`로 실행 작업 디렉터리의 `.env`를 읽고, `bootRun`의 작업 디렉터리는 해당 모듈 디렉터리(`backend/{모듈}/`)입니다. 그 위치에 `.env`를 두거나 실행 환경 변수로 주입하며, 값이 없으면 `application.yaml`의 로컬 기본값을 사용합니다.

### 관리자·부스 운영자 계정 부여

ADMIN·STOREHOST는 부여 API가 없어, 일반 회원가입 뒤 `auth_db`의 `users.role`을 직접 바꾸고 다시 로그인합니다(로컬 재현 구성에서는 시드 계정 사용).

```sql
-- auth_db 에서 실행. username 컬럼은 가입한 이메일입니다.
UPDATE users SET role = 'ADMIN'     WHERE username = '{가입한 이메일}';
UPDATE users SET role = 'STOREHOST' WHERE username = '{가입한 이메일}';
```

### Docker로 실행 (배포 구성)

`docker-compose.yml`은 배포용 구성(core-db, 5개 백엔드, frontend, nginx — nginx는 `/etc/letsencrypt` 인증서 전제)입니다. 백엔드 Dockerfile은 이미지 안에서 빌드하지 않고 `build/libs/*.jar`를 복사하므로, 이 구성으로 이미지를 빌드하려면 먼저 `cd backend && ./gradlew bootJar`를 실행해야 합니다.

배포는 `deploy` 브랜치 push → GitHub Actions(테스트·이미지 빌드) → GHCR push → 서버에서 pull 후 `docker compose up -d` 방식이며, `main` 머지는 자동 배포되지 않습니다. 저사양(1GB RAM) 서버는 `docker compose -f docker-compose.yml -f docker-compose.small.yml up -d`로 JVM 힙·MySQL 설정 override를 겹쳐 씁니다. 자세한 절차는 [docs/실행·배포 가이드.md](./docs/실행·배포%20가이드.md)를 참고하세요.

## 테스트

```bash
# 백엔드 전체 (H2 in-memory, MySQL 모드)
cd backend && ./gradlew test

# 모듈 단위
./gradlew :payment-service:test
```

- 백엔드: 75개 테스트 클래스, 513개 테스트(gateway 3개·21 / auth 13개·129 / festival 19개·136 / reservation 17개·83 / payment 23개·144), 실패·오류·건너뜀 0(2026-09-29 새로 clone한 저장소에서 `./gradlew test --rerun`). 테스트용 `application.yaml`은 H2를 쓰고, payment-service는 스케줄러와 데모 자동 입금을 끈 채 실행합니다.
- CI(`.github/workflows/ci.yml`)는 `main`·`deploy` 브랜치 push/PR마다 프론트·백엔드 테스트와 프론트 lint·build를 실행합니다.

| 서비스 | 검증 영역 | 대표 테스트 클래스 |
| --- | --- | --- |
| gateway | 공개 경로 통과, 클라이언트 신원 헤더 제거, 무효 토큰 401, HELPER 허용 목록·해지 세션 차단, 세션 확인 fail-closed | `JwtAuthenticationGlobalFilterTest`, `HelperSessionClientTest` |
| auth | 가입·로그인·재발급, 로그인 잠금, 재사용 탐지(유예 내 복구·유예 후 전체 폐기), 비밀번호 재설정, 이메일 인증 제한, 역할 부여 멱등, 도우미 초대 수명주기 | `UserAuthAcceptanceTest`, `PasswordResetAcceptanceTest`, `AuthServiceTest`, `EmailVerificationServiceTest`, `RoleServiceTest`, `HelperAccountServiceTest` |
| festival | 페스티벌 등록·심사·공개 조회, 주최 신청 승인 실패·재시도 복구, 행사 취소, 부스, 챗봇·AI 초안, 이미지 디코딩 검증, 내부 토큰 | `HostControllerAcceptanceTest`, `AdminFestivalControllerAcceptanceTest`, `HostApplicationReviewAcceptanceTest`, `FestivalCancellationAcceptanceTest`, `BoothAcceptanceTest`, `ChatbotServiceTest` |
| reservation | 동시 예매 초과 판매 없음, 1인 한도 동시성, 좌석 재고, 만료·복구 재시도, 환불 위약금 구간·19시 반환, 입장(동시 스캔 1회), 부스 대기열 | `ReservationAcceptanceTest`, `PurchaseLimitConcurrencyAcceptanceTest`, `RefundPolicyTest`, `SeatedRefundAcceptanceTest`, `CheckInAcceptanceTest`, `BoothWaitlistAcceptanceTest` |
| payment | 결제 성공·실패·금액 변조, 웹훅 중복·재시도, 이중 결제 보상 환불, 부분·전체 환불과 멱등키, 정산 계산·확정·보류·동시 확정 | `PaymentAcceptanceTest`, `PaymentCompensationAcceptanceTest`, `PaymentCancellationServiceTest`, `WebhookEventServiceTest`, `SettlementAcceptanceTest`, `SettlementCalculatorTest` |

Story별 테스트와 실행 기록은 [docs/테스트전략.md](./docs/테스트전략.md)를 참고하세요.

## 부하 테스트

운영 서버에는 부하를 주지 않고, 로컬에서 운영 조건(모든 서비스와 MySQL을 CPU 2개에 고정, `docker-compose.small.yml`, 서비스별 커넥션 풀 5개)을 재현해 [k6](https://k6.io/)로 측정했습니다(2026-09-27·28). 결과는 매번 DB의 예매 건수·잔여 재고·좌석 상태와 대조했습니다.

### 동시 요청 정합성

| 시나리오 | 결과 |
| --- | --- |
| 재고 100장에 1,000명 동시 요청 | 정확히 100건 성공 |
| 같은 좌석 1석에 500명 동시 요청 | 1명만 선점 |
| 남은 좌석 99석에 1,000명 무작위 요청 | 99석 판매, 한 좌석에 한 명 |
| 1인 한도 4장 계정으로 동시 100회 요청 | 4장까지만 성공 |

모든 시나리오에서 서버 오류와 초과 판매는 없었습니다.

### 처리량과 한계

예매는 초당 80건까지 안정적이었고(p95 66~75ms), 인기 티켓 한 종류에 요청이 몰리면 초당 약 100건에서 더 늘지 않았습니다. 원인을 나눠 측정해 보니 ① **같은 재고 행의 잠금 대기**(같은 티켓의 예매는 한 행을 차례로 차감), ② **DB 커넥션 점유**(예매 트랜잭션이 페스티벌 서비스 응답을 기다리는 동안에도 커넥션을 쥐고 있어 커넥션 5개 앞에 요청이 줄을 섬), ③ **CPU 2개**(사용률 80~90%)가 겹쳐 있었습니다.

| 예매를 초당 200건 보냈을 때 실제 처리량(건/초) | 티켓 1종 | 티켓 4종으로 분산 |
| --- | ---: | ---: |
| 운영 설정 (CPU 2개, 커넥션 5개) | 108~112 | 113~115 |
| 커넥션 10개 | 115 | 135 |
| CPU 4개 | 116 | 146 |

운영 설정에서는 재고를 나눠도 처리량이 거의 늘지 않았고, 커넥션이나 CPU 여유가 생긴 뒤에야 분산 효과가 나타났습니다. 측정 중 찾은 문제는 한도 조회 인덱스(#339)와 동시 접속 시 연결 끊김(#340)으로 고쳤습니다.

**다음 단계(미구현):** 행사 조회를 예매 트랜잭션 밖으로 옮기고 예매 전용 내부 조회 API를 만들어 커넥션 점유를 줄인 뒤, 재고를 여러 행으로 나눕니다. 요청이 몰리는 순간의 대기는 대기열로 관리합니다.

> 로컬 재현이라 운영 서버의 CPU 성능·스왑·HTTPS는 반영되지 않았고, 원인 분석 실험은 조건마다 1~2회 측정한 값입니다.

## 주요 기술 의사결정

검토한 대안을 포함한 전체 결정 기록은 [docs/아키텍처.md](./docs/아키텍처.md)의 "결정 기록"에 있습니다.

### 인증은 게이트웨이에서 한 번만 하고, 서비스는 전달된 신원 헤더를 신뢰

- 상황: 5개 서비스가 각자 JWT를 파싱·검증하면 서명 키와 검증 로직이 서비스마다 복제되고, 역할이 늘 때마다(HELPER 등) 모든 서비스를 고쳐야 합니다.
- 결정: `JwtAuthenticationGlobalFilter`가 토큰을 검증한 뒤 `X-User-Id`·`X-User-Role`(+`X-Token-Iat`, HELPER는 `X-Festival-Id`)를 붙여 전달하고, 클라이언트가 보낸 같은 이름의 헤더는 공개 경로를 포함해 항상 먼저 제거합니다. 각 서비스는 `permitAll` + STATELESS로 두고 컨트롤러가 헤더를 직접 받으며, HELPER처럼 허용 범위가 좁은 역할은 게이트웨이에서 "기본 차단 + 허용 목록"으로 한곳에서 통제합니다.
- 결과·한계: 서비스 코드는 인증 없이 도메인 로직에 집중하고, 헤더 스푸핑·HELPER 제한은 `JwtAuthenticationGlobalFilterTest`로 검증했습니다. 이 구조는 서비스가 게이트웨이를 통해서만 접근되는 내부 네트워크를 전제로 합니다. 역할 검사는 서비스마다 역할 문자열을 비교하므로, 공유 enum이 없어 역할이 바뀌면 여러 서비스를 함께 고쳐야 합니다.

### Refresh Token 재사용 탐지에 5초 유예를 둔 rotation

- 상황: Refresh Token을 재발급마다 교체하고 폐기된 토큰의 재사용을 탈취로 보아 전체 로그아웃시키자, 새로고침을 연달아 하는 정상 사용자도 이전 재발급 응답이 반영되기 전 같은 토큰으로 재발급을 시도해 로그아웃되는 일이 생겼습니다.
- 결정: `AuthService#reissue`가 폐기된 토큰을 받으면, 폐기된 지 5초 이내일 때만 `replacedByTokenId` 체인을 따라 현재 유효한 최신 토큰으로 정상 교체합니다. 유예를 넘겼거나 체인이 끊겼으면 `REQUIRES_NEW` 트랜잭션으로 사용자의 모든 토큰을 폐기하고 401 `REFRESH_TOKEN_REUSED`를 반환합니다.
- 결과·한계: 동시 새로고침 경합은 자연 복구되고, 유예를 넘긴 재사용은 여전히 전체 폐기됩니다(`UserAuthAcceptanceTest`의 유예 내·후 시나리오). 대신 폐기 직후 5초 안에 일어난 재사용은 경합인지 탈취인지 구분하지 않고 허용합니다.

### 서비스 간 호출 실패는 "중간 상태 + 멱등 키 + 재시도 스케줄러"로 복구

- 상황: 주최 신청 승인(festival → auth 역할 부여)과 페스티벌 공개(festival → reservation 좌석 생성)는 두 서비스의 상태가 함께 바뀌어야 하지만, 분산 트랜잭션이나 메시지 큐는 없습니다. 응답이 유실되면 한쪽만 반영된 채 멈출 수 있습니다.
- 결정: 외부 호출 전에 `APPROVAL_PENDING`·`PUBLISH_PENDING` 같은 중간 상태를 남기고, 호출이 성공하면 최종 상태로 확정합니다. 실패하면 중간 상태를 유지하고 재시도 스케줄러가 60초마다(30초 이상 머문 건) 같은 요청을 다시 보냅니다. 받는 쪽은 신청 ID(`role_grants.application_id` unique)·티켓 타입 ID 기준으로 멱등하게 처리해 중복 반영을 막습니다. 주최 신청 승인은 이를 위해 `@Transactional`을 의도적으로 걸지 않았습니다.
- 결과·한계: 운영자가 승인 버튼을 다시 누르지 않아도 스스로 회복되며, 같은 패턴을 재고 반환 대기열·웹훅 재시도·보상 환불·행사 취소 일괄 환불에도 적용했습니다. 대신 회복까지 최대 1~2분의 지연이 있고, 재시도가 계속 실패해도 로그 외에 별도 알림은 없습니다.

### 1인 구매 한도는 (사용자, 페스티벌) 잠금 행으로 직렬화

- 상황: 같은 사용자가 동시에 예매를 보내면 두 요청이 모두 "아직 산 게 없음"을 읽고 한도를 통과할 수 있습니다. 예매 테이블을 `SELECT ... FOR UPDATE`로 잠가 합산하는 방식은 MySQL 기본 격리 수준에서 다른 사용자의 행·빈 구간까지 잠가 전체 예매가 줄을 서거나 교착이 났습니다.
- 결정: 수량을 담지 않는 별도 잠금 행 `PurchaseLimitLock`((user_id, festival_id) unique)을 `INSERT ... ON DUPLICATE KEY UPDATE`로 준비한 뒤 `PESSIMISTIC_WRITE`로 잡고, 보유 수량 합산은 잠금 없이 읽습니다. `createReservation`을 `READ COMMITTED`로 실행해 잠금을 기다린 뒤의 조회가 앞 요청이 커밋한 예매를 보게 했습니다.
- 결과·한계: 잠금 범위가 같은 사용자·같은 페스티벌로 좁혀져 다른 사용자의 예매를 막지 않으며, 동시 다중 예매에서도 한도 초과가 0건임을 `PurchaseLimitConcurrencyAcceptanceTest`로 확인했습니다. 같은 사용자의 동시 요청은 순서대로 대기하고, 한도 확인만을 위한 잠금 행이 (사용자, 페스티벌)마다 하나씩 쌓입니다.

### 결제 성공은 PG 재조회 결과로만 판정

- 상황: 브라우저가 전달하는 결제 결과나 웹훅 본문의 금액을 그대로 믿으면 금액 변조나 다른 상점·채널 결제를 정상 결제로 처리할 위험이 있고, 완료 API와 웹훅이 서로 다른 로직을 타면 상태가 어긋날 수 있습니다.
- 결정: 결제 준비 단계에서 서버가 금액을 정해 `Payment`(READY)를 만들고, 완료 요청이나 웹훅이 오면 `PaymentService#syncPayment`가 PortOne에 결제를 재조회해 상점 ID·채널 키·채널 유형(테스트)·통화·금액을 우리 주문과 대조합니다. 하나라도 다르면 409 `PAYMENT_VERIFICATION_FAILED`로 거부합니다. 완료 API와 웹훅은 같은 `syncPayment`를 공유하고(이미 확정된 결제는 현재 상태를 그대로 반환), 같은 PG 거래는 `transactionId`로 한 번만 기록하며, 외부 호출 동안에는 DB 트랜잭션을 열어두지 않습니다.
- 결과·한계: 금액 변조 거부, 웹훅만으로 확정, 중복 웹훅 1회 처리를 `PaymentAcceptanceTest`로 검증했습니다. 결제마다 PG 조회가 한 번 더 필요하고, 현재 검증은 테스트 채널 결제만 통과시키므로 실결제 채널로 전환할 때는 검증 조건과 가상계좌 데모 자동 입금 설정을 함께 바꿔야 합니다.

## Troubleshooting

### payment-service 배포 직후 무한 재시작으로 전체 API 지연

- 문제: payment-service를 처음 배포한 직후 컨테이너가 계속 재시작되며 1GB RAM 서버의 CPU를 점유해, 로그인을 포함한 모든 API가 극도로 느려졌습니다.
- 조사: 서버에서 직접 확인한 원인은 세 가지였습니다. ① core-db 볼륨이 payment_db 도입 전부터 있어 `mysql-init/core-db/03-payment-db.sql`이 한 번도 실행되지 않았고, ② payment-service Dockerfile만 exec-form `ENTRYPOINT`라 셸을 거치지 않아 `docker-compose.small.yml`의 `JAVA_OPTS`(힙 제한)가 적용되지 않았으며, ③ 기존 3개 서비스가 HikariCP 기본 풀(10개씩)만으로 MySQL `max_connections` 30을 채워 4번째 서비스가 커넥션을 받지 못했습니다.
- 해결·검증: 운영 DB에 스키마 스크립트를 수동 실행하고, payment-service Dockerfile을 다른 서비스와 같은 shell-form(`sh -c "exec java $JAVA_OPTS -jar app.jar"`)으로 통일했습니다. 4개 서비스의 `maximum-pool-size`를 5로 낮추고 `docker-compose.small.yml`에서 `--max-connections=60`으로 올렸습니다. 재시작 후 재시작 횟수 0, CPU 정상화를 확인했습니다.

### 예매 오픈 순간 Connection reset

- 문제: 예매가 열리는 순간 일부 요청이 `Connection reset`으로 실패했습니다.
- 조사: 게이트웨이가 reservation-service로 연결을 한꺼번에 새로 열면서 Tomcat 기본 연결 대기열(`accept-count` 100)이 넘쳤습니다. 로컬 부하 테스트(재시작 직후 1,000명 동시 예매)에서 5~33%가 끊겼습니다.
- 해결·검증: reservation-service `application.yaml`의 `server.tomcat.accept-count`를 1000으로 올렸고, 같은 부하 테스트에서 끊김이 0건이 되었습니다.

### 같은 예매를 두 탭에서 결제하거나 만료 후 결제가 성공하는 문제

- 문제: 같은 예매를 두 탭에서 결제하거나 10분 홀드가 만료된 뒤 결제를 마치면, 예매 확정은 거절되는데 결제는 이미 승인되어 돈만 빠져나간 결제가 남았습니다.
- 조사: reservation-service의 확정 처리는 이미 다른 결제로 확정된 예매와 만료된 예매를 409로 거절하지만(`ReservationService#confirmReservation`), payment-service는 이 거절을 받은 뒤 PG 결제를 되돌리는 경로가 없었습니다.
- 해결·검증: 예매 확정이 409로 거절되면 `PaymentService`가 결제에 거절 표시를 남기고 결제별 고정 멱등키로 자동 전액 환불(`PaymentCancellationService#compensate`, 사유 `RESERVATION_NOT_CONFIRMED`)을 요청합니다. 환불 요청이 실패해도 거절 표시가 남아 `PaymentCompensationScheduler`가 같은 멱등키로 재시도하며, 거절된 결제는 정산과 행사 취소 일괄 환불에서 제외합니다. `PaymentCompensationAcceptanceTest`(나중에 확정되는 결제 자동 환불, 환불 실패 후 재시도로 완료), `PaymentAcceptanceTest`(만료 후 확정 거부), `SettlementAcceptanceTest`(보상 환불 결제 정산 제외)로 검증했습니다.

### 의사결정으로 이어진 문제 ([주요 기술 의사결정](#주요-기술-의사결정) 참고)

- **새로고침을 연달아 하면 로그인이 풀리는 문제** → 의사결정 "Refresh Token 재사용 탐지에 5초 유예를 둔 rotation" 참고
- **동시 예매 시 1인 한도 초과, 이후 다른 사용자 예매까지 대기·교착** → 의사결정 "1인 구매 한도는 (사용자, 페스티벌) 잠금 행으로 직렬화" 참고
- **서비스 간 호출 실패로 주최 승인·페스티벌 공개가 중간 상태에 멈춤** → 의사결정 "서비스 간 호출 실패는 '중간 상태 + 멱등 키 + 재시도 스케줄러'로 복구" 참고

전체 14건의 원인·해결 기록은 [docs/트러블슈팅.md](./docs/트러블슈팅.md)를 참고하세요.

## 팀과 기여

| 서비스 · 도메인 | 담당 |
| --- | --- |
| Gateway (라우팅·JWT 검증·HELPER 제한) |최승환, 조민규 |
| 회원·인증 (auth-service) |최승환, 조민규 |
| 운영자 회원·주최자 관리 |최승환 |
| 주최자 신청·페스티벌 등록·심사 (festival-service) |송시훈 |
| 행사 취소 |조민규, 최승환 |
| 부스·대기열 |송시훈 |
| AI 추천 챗봇 |조민규 |
| AI 등록 초안·카카오맵 |송시훈 |
| 예매·좌석·입장 (reservation-service) |최승환 |
| 도우미 계정 |조민규 |
| 결제·환불 (payment-service) |조민규 |
| 정산 |조민규 |
| 프론트엔드 |최승환(가장 많은 화면), 조민규·송시훈(담당 기능 화면) |
| CI/CD·배포 |조민규 |

- 위 표는 2026-09-29 main의 커밋 작성자와 파일별 변경 줄 수를 기준으로 정리했습니다. 개발은 3명이 했고, 4명 중 1명은 초반에 팀에서 빠졌습니다.

## Documentation

| 문서 | 위치 | 용도 |
| --- | --- | --- |
| 요구사항·설계·회의록 | [팀 Notion](https://app.notion.com/p/5-5-3c973873401a80788cedccf3453d5810) | 요구사항, 설계 문서, 결정 이력 (최신본) |
| 문서 인덱스 | [`docs/README.md`](./docs/README.md) | Notion 원본과 저장소 사본의 대응 목록 |
| 설계 문서 | [요구사항](./docs/요구사항.md) · [API](./docs/API.md) · [ERD](./docs/ERD.md) · [권한메트릭스](./docs/권한메트릭스.md) · [서비스경계](./docs/서비스경계.md) · [시퀀스](./docs/시퀀스.md) · [아키텍처](./docs/아키텍처.md) · [화면설계](./docs/화면설계.md) | Notion 문서를 내보낸 저장소 사본 |
| 실행·테스트·회고 문서 | [실행·배포 가이드](./docs/실행·배포%20가이드.md) · [테스트전략](./docs/테스트전략.md) · [테스트체크리스트](./docs/테스트체크리스트.md) · [공통완료기준](./docs/공통완료기준.md) · [트러블슈팅](./docs/트러블슈팅.md) · [스프린트리뷰](./docs/스프린트리뷰.md) · [retrospective](./docs/retrospective.md) | Notion 문서를 내보낸 저장소 사본 |
| 발표 자료 | [`docs/presentation/FevalGo_final_presentation.pdf`](./docs/presentation/FevalGo_final_presentation.pdf) | 최종 발표 슬라이드 |
| 수동 API 호출 예시 | [`backend/auth-service/src/test/java/org/example/authservice/auth/`](./backend/auth-service/src/test/java/org/example/authservice/auth) (`auth.http`, `emailverification.http`, `oauth.http`), [`user/user.http`](./backend/auth-service/src/test/java/org/example/authservice/user/user.http) | IntelliJ HTTP Client용 인증·회원 API 호출 예시 |
| 환경 변수 예시 | [`.env.example`](./.env.example), [`frontend/.env.example`](./frontend/.env.example) | 필요한 키 목록 |
| DB 초기화 | [`mysql-init/core-db/`](./mysql-init/core-db) | 스키마·서비스별 계정 생성 |
| 컨테이너·프록시 구성 | [`docker-compose.yml`](./docker-compose.yml), [`docker-compose.small.yml`](./docker-compose.small.yml), [`nginx/nginx.conf`](./nginx/nginx.conf), [`docker-compose.local.yml`](./docker-compose.local.yml), [`nginx-local/`](./nginx-local) | 실행·배포 구성, 로컬 재현 |
| CI/CD | [`.github/workflows/ci.yml`](./.github/workflows/ci.yml), [`.github/workflows/deploy.yml`](./.github/workflows/deploy.yml) | 테스트·이미지 빌드·배포 |
| 이슈·PR 템플릿 | [`.github/ISSUE_TEMPLATE/`](./.github/ISSUE_TEMPLATE), [`.github/PULL_REQUEST_TEMPLATE.md`](./.github/PULL_REQUEST_TEMPLATE.md) | 작업 단위·리뷰 양식 |

## 개선 계획

- **페스티벌 수정·삭제 API**: 등록 후 수정·삭제 엔드포인트가 없습니다(도우미 계정도 등록 시점의 종료 시각을 복사해 사용).
- **주최자별 구매 한도 설정**: 1인 한도는 전역 설정(`RESERVATION_MAX_QUANTITY_PER_FESTIVAL`)뿐이며, 페스티벌별로 낮추는 기능은 후속 작업입니다.
- **STOREHOST 부여 흐름**: 부스 운영자 역할은 신청·승인 흐름 없이 DB에서 직접 부여합니다.
- **빌링키(정기결제)**: `PORTONE_CHANNEL_KEY_BILLING` 설정 키만 있고 사용하는 코드는 없습니다.
- **실결제 채널 전환**: 결제 검증이 테스트 채널 기준이고 가상계좌는 데모 자동 입금이라, 전환 시 검증 조건과 입금 처리 방식을 바꿔야 합니다.
- **정산 지급 자동화**: 지급은 운영자가 송금 확인번호를 기록하는 수동 방식(`MARK_PAID`)입니다.
- **부스 대기열 알림**: 15초 폴링(`/api/booth-waitlists/me/active`) → 푸시·WebSocket 전환.
- **스키마 마이그레이션 도구 도입**: 전 서비스가 `ddl-auto: update`로 스키마를 관리합니다.
- **테스트 보강**: 운영자 회원 정지(`AdminUserService`), 운영 현황·대시보드 요약(`FestivalOperationService`, `AdminSummaryService`) 전용 테스트와, 여러 서비스를 함께 띄우는 자동 E2E 테스트가 없습니다.
- **트래픽 대기열**: Story 12(대기열)는 구현하지 않았습니다. 초과 판매는 재고 조건부 차감과 구매 한도 잠금으로 막고 있으며, 예매가 몰리는 순간의 대기 관리는 후속 과제입니다(부하 테스트의 "다음 단계" 참고).
