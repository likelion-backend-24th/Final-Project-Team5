# API
<aside>
⚡

외부 HTTP 계약, 서비스 간 동기 호출, 비동기 Event 계약을 정의합니다. 현재 Sprint에 필요하지 않은 계약은 미리 상세화하지 않습니다.

</aside>

관련 Story: 각 Operation 표의 관련 Story·시나리오 행 · 관련 업무 규칙: 요구사항 문서 - 4. 업무 규칙 · 호출 방향: 서비스 경계 문서 - 통신 방향·실패 · 권한: 권한 Matrix 문서 · 호출 순서: 시퀀스 문서

2026-09-28 main 코드 기준으로 다시 확인했습니다. 코드에 OpenAPI 스펙 파일과 `operationId` 선언이 없으므로 식별자는 `METHOD /path`를 기본으로 쓰고, 팀이 이미 붙여 쓰던 이름(signUp 등)은 그대로 병기합니다. 팀 이름이 없던 API는 새 이름을 만들지 않고 `METHOD /path`만 적습니다. 핵심 흐름은 Operation별 상세 표로, 나머지는 아래 전체 엔드포인트 목록 표로 둡니다(모든 엔드포인트는 둘 중 한 곳에 한 번씩 등재).

## 공통 규약

| 항목 | 계약 |
| --- | --- |
| 인증 | `Authorization: Bearer {JWT}` — 로그인(`POST /api/auth/login`) 응답으로 발급, 이후 모든 요청에 동봉. **검증은 Gateway가 담당** (각 서비스는 재검증 안 함). 검증 성공 시 `X-User-Id`, `X-User-Role`, `X-Token-Iat`(발급 시각), HELPER만 `X-Festival-Id` 헤더로 사용자 정보를 다운스트림 서비스에 전달 (구현: `JwtAuthenticationGlobalFilter`, `backend/gateway`). 공개 라우트(`/api/auth/**`, `/api/festivals/**`, `/api/ticket-types/**`, `/api/booths/**`, `/api/v1/webhooks/**`, `/ws/**`)는 검증 없이 통과, 나머지는 모두 토큰 필수(`/api/store/booths/**`·`/api/booth-waitlists/**`·`/api/chatbot/**` 포함). 클라이언트가 보낸 `X-User-*`·`X-Festival-Id`·`X-Token-Iat` 헤더는 항상 제거 후 재설정(스푸핑 방지). HELPER 토큰은 허용 경로 4개(`GET /api/users/me`, `POST /api/organizer/reservations/verify`, `POST /api/organizer/reservations/verify-code`, `GET /api/organizer/reservations/check-in-stats`)와 공개 라우트 외에는 Gateway가 403(본문 없음)으로 막고, 허용 경로도 요청마다 인증 서비스 `GET /internal/v1/helper-accounts/session`으로 세션을 확인한다(실패·3초 Timeout이면 401). 각 서비스는 Spring Security 인가 규칙 없이(permitAll) 서비스 계층에서 `X-User-Role` 문자열을 비교해 역할을 검사한다 |
| 경로 규칙 | 외부 API는 `/api/**`(버전 접두사 없음)이고, PortOne 웹훅만 `/api/v1/webhooks/**`, 서비스 간 내부 API는 `/internal/v1/**`이다. Gateway에는 `/internal/**` 라우트가 없다 |
| URL 규칙 | 리소스는 복수형 명사 (`/festivals`, `/reservations`, `/ticket-types`), 단건은 `/festivals/{id}`, 페이징은 `?page=0&size=20&sort=createdAt,desc` |
| Gateway 라우팅 | `/api/auth/**`, `/api/users/**`, `/api/admin/hosts/**`, `/api/admin/users/**` → auth-service · `/api/festivals/**`, `/api/ticket-types/**`, `/api/host/festivals/**`, `/api/host-applications/**`, `/api/admin/host-applications/**`, `/api/admin/festivals/**`, `/api/booths/**`, `/api/store/booths/**`, `/api/chatbot/**` → festival-service · `/api/festivals/*/ticket-types/*/seats`(festival-service 라우트보다 먼저 선언), `/api/reservations/**`, `/api/organizer/reservations/**`, `/api/booth-waitlists/**`, `/ws` → reservation-service · `/api/payments/**`, `/api/admin/settlements/**`, `/api/host/settlements/**` → payment-service · `/api/v1/webhooks/**` → payment-service(PortOne 서명 인증이라 공개 라우트, JWT 불필요). `/api/ticket-types/**` 라우트는 선언돼 있으나 이 경로를 받는 festival-service 컨트롤러는 없다 |
| Trace ID | `X-Trace-Id` — Gateway가 요청에 없으면 UUID로 생성하고(있으면 그대로 사용) 다운스트림 요청과 응답 헤더에 싣는다 (구현: `TraceIdGlobalFilter`). 각 서비스 코드는 이 헤더를 읽거나 로그·서비스 간 내부 호출에 이어 붙이지 않는다 |
| 성공 Envelope | 외부 API 공통 `ApiResponse` — 필드 success, data, meta, message, errorCode(4개 서비스가 같은 모양의 클래스를 각자 보유). 예: `{"success": true, "data": {...}, "meta": null, "message": "설명", "errorCode": null}`. 목록(페이징) API는 `meta.pagination`을 채운다: `{"pagination": {"page": 0, "size": 20, "totalItems": 127, "totalPages": 7, "hasNext": true, "hasPrev": false}}`. 페이징이 없는 API는 `meta`가 null로 나간다(null 필드 생략 설정 없음). 예외: 웹훅은 본문 없이 상태 코드만, 소셜 로그인 콜백은 302 리다이렉트 |
| 실패 Envelope | `{"success": false, "data": null, "meta": null, "message": "설명", "errorCode": "ERROR_CODE"}` — HTTP 상태는 각 서비스 ErrorCode enum에 정의된 값. Gateway가 막는 401·403은 본문이 없다. 전역 fallback 예외 핸들러가 없어 처리되지 않은 예외(예: 내부 호출 Timeout이 그대로 전파된 경우)는 이 Envelope가 아닌 Spring 기본 오류 응답(500)으로 나간다 |
| Validation 오류 | 요청 본문 검증 실패는 400. 에러코드와 메시지 형식이 서비스마다 다르다 — auth-service는 `VALIDATION_ERROR`(첫 필드의 오류 메시지만), festival·reservation·payment-service는 `INVALID_REQUEST`(필드명 뒤에 오류 메시지를 붙인 형식). auth-service만 필수 쿠키 누락을 400 `MISSING_COOKIE`로, festival-service만 업로드 용량 초과를 400 `INVALID_IMAGE_SIZE`로, payment-service만 낙관적 락·무결성 충돌을 409 `CONCURRENT_MODIFICATION`으로 따로 매핑한다 |
| 시간·Timezone | 행사 일정 값(페스티벌 시작·종료, 티켓 판매 기간 등)은 Timezone 없는 `LocalDateTime` 벽시계 값으로 저장·전달하고, 비교할 때는 `app.timezone`(환경변수 APP_TIMEZONE, 기본 Asia/Seoul) 기준 현재 시각을 쓴다. 예매 만료(`expiresAt`)·입장(`checkedInAt`)·결제 승인(`paidAt`)·취소 시각(`cancelledAt`) 같은 시점 값은 `Instant`(UTC)로 저장하고 JSON에는 ISO-8601 UTC 문자열로 나간다(reservation-service는 `hibernate.jdbc.time_zone: UTC`로 고정). 정산 조회 필터 from·to도 `Instant`. 정산 배치는 Asia/Seoul 기준 매일 02:00 |
| 멱등성 | `Idempotency-Key` 헤더를 쓰는 외부 API는 2개다. ① `POST /api/payments/{paymentId}/cancellations` — 선택. 같은 키로 다시 오면 새로 취소하지 않고 PortOne 재조회로 맞춘 기존 취소 결과를 돌려준다(다른 결제에 같은 키면 403). 키가 없으면 서버가 UUID를 만들어 재시도 보호 없이 처리하고, PortOne 취소 요청에도 같은 키를 전달한다. ② `POST /api/admin/settlements/{id}/{action}` — 필수. 빈 값이거나 100자 초과면 400 `IDEMPOTENCY_KEY_REQUIRED`(헤더 자체가 없으면 전역 핸들러가 401 `UNAUTHORIZED`), 같은 키·같은 내용은 이전 처리 결과, 같은 키·다른 내용은 409 `IDEMPOTENCY_KEY_CONFLICT`. 헤더 없이 멱등을 보장하는 계약: 역할 부여(applicationId), 좌석 생성(ticketTypeId), 예매 확정(paymentId), 예매 취소 재호출(이미 CANCELLED면 무시), 환불 반영(PortOne 취소 ID), 웹훅(webhook_id unique), 보상 환불·행사 취소 환불(결제별 고정 키), 행사 취소 요청 재호출(현재 상태 반환) |
| 401 Header | Gateway가 내는 401(토큰 없음·서명 무효·HELPER 세션 확인 실패)에만 `WWW-Authenticate: Bearer`가 붙고 본문은 없다 (구현: `JwtAuthenticationGlobalFilter`). 각 서비스가 `X-User-Id` 등 필수 헤더가 없을 때 내는 401(`UNAUTHORIZED`, ApiResponse 본문)과 업무상 401(예: `INVALID_PASSWORD`, `REFRESH_TOKEN_REUSED`, 내부 토큰 불일치 `INVALID_INTERNAL_TOKEN`)에는 이 헤더를 붙이지 않는다(서비스 코드에 설정 없음) |
| 내부 API | Gateway·외부 비노출, 호출 관계별 환경 변수 Bearer Token(`Authorization: Bearer`  • 토큰). 토큰 환경변수 이름은 festival → reservation 좌석 생성(`POST /internal/v1/seats`)만 INTERNAL_RESERVATION_TOKEN, 나머지(Gateway → auth 포함)는 모두 INTERNAL_AUTH_TOKEN. 응답 봉투는 서비스마다 다르다 — auth-service 내부 API와 festival-service 재고 API(`/internal/v1/ticket-types/**`)는 ApiResponse 봉투, festival-service `/internal/v1/festivals/**`, reservation-service `/internal/v1/reservations/**`·`/internal/v1/seats`, payment-service `/internal/v1/payments/**`는 봉투 없이 DTO·배열·빈 본문. 배포(docker compose)에서는 서비스 간 URL을 컨테이너 DNS 이름(예: `RESERVATION_SERVICE_URL=http://reservation-service:8083`)으로 compose의 environment가 지정한다 — 로컬 개발용 `localhost` 값이 남거나 환경변수가 빠지면 코드 기본값 `localhost`로 폴백돼 호출이 실패한다(2026-09-18 장애) |
| 클라이언트 캐시 | 프론트가 `GET /api/festivals`(목록)·`GET /api/festivals/{id}`(상세)를 10초간 캐시하고 이후엔 캐시를 먼저 그린 뒤 뒤에서 재검증한다. 예매 생성·취소가 성공하면 페스티벌 캐시를 비운다. 상세 GET은 조회수를 집계하므로 프론트는 카드 pointerdown 시점에만 프리페치한다. 그 밖의 API(인증·예매·결제·좌석)는 캐시하지 않는다 |

## 외부 HTTP 계약 — Sprint 1

### signUp — `POST /api/auth/signup`

방문자를 참가자 계정으로 회원가입

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Body: name, username(email), nickname, password(8자 이상), termsAgreed |
| 정상 | `201` — 참가자 계정 생성(role USER·status ACTIVE 자동 부여), 비밀번호는 BCrypt 해시로 저장(`BCryptPasswordEncoder` 기본 설정). 같은 이메일의 소셜 전용 계정이 있으면 새 계정을 만들지 않고 그 계정에 비밀번호를 붙여 연동 |
| 실패 | 입력 검증 실패(이메일 형식·비밀번호 8자 미만·이름·닉네임 누락) `400`(VALIDATION_ERROR) / 약관 미동의(termsAgreed=false) `400`(TERMS_NOT_AGREED) / 이메일·닉네임 중복 `409`(DUPLICATE_USERNAME·DUPLICATE_NICKNAME), 계정 미생성(이메일 중복 금지 규칙). 이메일 인증 미완료 시 `400`(EMAIL_NOT_VERIFIED) — 먼저 인증코드 발송(`POST /api/auth/email/send`)·확인(`POST /api/auth/email/verify`)을 거쳐야 하며, 가입이 성공하면 그 인증 기록은 삭제된다 |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### login — `POST /api/auth/login`

이메일·비밀번호로 로그인해 Access·Refresh Token을 발급한다

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Body: username(email), password |
| 정상 | `200` — Access Token은 body로 반환(유효 1시간), Refresh Token은 HttpOnly 쿠키 refreshToken(Secure, SameSite=Strict, Path /, 유효 14일)로 반환 후 DB에는 SHA-256 해시만 저장. 응답 헤더 `Cache-Control: no-store` |
| 실패 | 존재하지 않는 계정 `404`(USER_NOT_FOUND) / 비밀번호 불일치 `401`(INVALID_PASSWORD) — 실패 5회면 10분 잠금 / 잠긴 계정 `403`(ACCOUNT_LOCKED) / 정지 계정 `403`(ACCOUNT_SUSPENDED) / 탈퇴 계정 `403`(ACCOUNT_WITHDRAWN) / 비밀번호 없는 소셜 전용 계정 `409`(SOCIAL_LOGIN_REQUIRED) / 활성화 전·해지·행사 종료 도우미 계정 `403`(HELPER_PENDING_ACTIVATION)·`410`(INVITATION_REVOKED·HELPER_FESTIVAL_ENDED) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### reissue — `POST /api/auth/reissue`

Refresh Token으로 Access Token을 재발급하고, 탈취 여부를 검사한다

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Cookie: refreshToken (본문 없음) |
| 정상 | `200` — 새 Access Token(body)·새 Refresh Token(쿠키) 반환, 기존 Refresh Token 폐기(Rotation). 재발급 때 DB의 최신 role로 Access Token을 만든다. 폐기된 지 5초 안의 재사용은 교체 체인을 따라 최신 토큰 기준으로 정상 재발급(새로고침 경합 완화) |
| 실패 | 쿠키 없음 `400`(MISSING_COOKIE) / 만료·무효 Refresh Token `401`(INVALID_REFRESH_TOKEN) / 폐기 후 5초가 지난 재사용 `401`(REFRESH_TOKEN_REUSED) + 해당 사용자 전체 Refresh Token 무효화(Refresh Token 재사용 감지 규칙) / 도우미 세션 버전 불일치 `401`(HELPER_SESSION_REVOKED) / 정지·탈퇴 계정 `403` |
| 보안 | Refresh Token 쿠키 필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### submitHostApplication — `POST /api/host-applications`

회원이 페스티벌 주최자가 되기 위한 신청을 제출한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 3 / 시나리오 3 |
| Request | Body: introduction(소개, 필수, 1000자 이내), contact(연락처, 필수, 255자 이내) |
| 정상 | `201` — PENDING 상태로 신청 생성. 반려된 뒤 재신청 허용 |
| 실패 | 인증 없음 `401` / ADMIN이 신청 `403`(FORBIDDEN_ROLE) / 이미 HOST Role 보유 `409`(ALREADY_HOST) / PENDING 신청이 이미 있음 `409`(DUPLICATE_APPLICATION)(활성 신청 1건만 허용, 기존 주최자 재신청 불가 규칙) / 입력 검증 실패 `400`(INVALID_REQUEST) / HELPER 토큰은 Gateway에서 `403` |
| 보안 | 로그인 필요. 서비스 코드는 ADMIN·HOST만 막으므로 USER와 STOREHOST가 신청할 수 있다 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### getMyHostApplication — `GET /api/host-applications/me`

본인의 주최 신청 상태·반려사유를 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 3 / 시나리오 3 |
| Request | - |
| 정상 | `200` — 본인 최신 신청 상태, 반려 시 반려사유 포함 |
| 실패 | 인증 없음 `401` / 신청 이력 없음 `404`(APPLICATION_NOT_FOUND) (참고: 이 API는 X-User-Id만으로 본인 신청을 조회하는 구조라 실제로는 타인 신청을 조회할 경로 자체가 없음) |
| 보안 | 로그인 필요(본인) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### listHostApplications — `GET /api/admin/host-applications`

운영자가 주최 신청 목록을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 4 / 시나리오 4 |
| Request | 없음 — 상태 필터·페이징 쿼리 파라미터를 받지 않는다 |
| 정상 | `200` — 심사 대기(PENDING)뿐 아니라 APPROVAL_PENDING·APPROVED·REJECTED까지 모든 신청을 최신순으로 반환. 신청자 이름·이메일은 인증 서비스 `GET /internal/v1/users`로 한 번에 조회해 붙이고, 그 조회가 실패하면 신청자 정보만 비운 채 목록은 그대로 반환 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE)(신청 심사는 운영자만 규칙) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### reviewHostApplication — `PATCH /api/admin/host-applications/{id}`

운영자가 주최 신청을 승인·반려하고, 승인 시 주최자 권한을 부여한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 4 / 시나리오 4 |
| Request | Path: id / Body: status(APPROVED 또는 REJECTED), rejectReason(반려 시 필수) |
| 정상 | 반려 `200`(REJECTED, Role 미부여) / 승인: 신청을 APPROVAL_PENDING으로 먼저 저장한 뒤 인증 서비스 grantHostRole(`PUT /internal/v1/roles`)을 호출 — 성공하면 `200`(APPROVED, Role 부여 완료), 응답 유실·Timeout이면 `202`(APPROVAL_PENDING 유지). APPROVAL_PENDING 신청은 같은 승인 요청을 다시 보내거나 HostApplicationApprovalRetryScheduler(60초마다, 30초 이상 머문 건)가 같은 applicationId로 재시도해 APPROVED로 확정 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) / 없는 신청 `404`(APPLICATION_NOT_FOUND) / 이미 APPROVED·REJECTED인 신청에 재결정 `409`(ALREADY_REVIEWED) / APPROVAL_PENDING 신청 반려 `409`(APPROVAL_PENDING_CANNOT_REJECT) / 반려 사유 없음 `400`(REJECT_REASON_REQUIRED) / status가 승인·반려가 아님 `400`(INVALID_DECISION) (승인 처리 멱등성, 계정 재발급 없이 권한만 추가 규칙 — 기존 Access Token의 role은 재발급 때 반영) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (재시도 배치·read 5초는 Sprint 2) |

### createFestival — `POST /api/host/festivals`

승인된 주최자가 새 페스티벌(및 티켓 종류)을 등록한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 5 / 시나리오 5 |
| Request | Body: 이름, 설명(1000자 이내), 시작·종료일시(시작은 미래), 지역(region)·상세 장소, 위도·경도(둘 다 넣거나 둘 다 비움), 입장·운영 시간, 카테고리, `stageLayout`(`FRONT_STAGE`/`CENTER_STAGE`, 필수), 대표 이미지 URL(최대 1장), 본문 이미지 URL(최대 2장), 티켓종류(이름·가격·ticketMode·판매 기간·티켓 날짜. STANDING은 quantity, SEATED는 zone·seatLayout(행별 seatCount·excludedSeats)·positionRow/positionCol(FRONT_STAGE) 또는 positionAngle(CENTER_STAGE)) |
| 정상 | `201` — **PENDING**(심사 대기) 상태로 등록. 방문자 목록·상세에는 공개 승인 전까지 보이지 않음 |
| 실패 | 필수값 누락·시작일시가 과거·소개 1000자 초과 등 입력 검증 실패 `400`(INVALID_REQUEST) / STANDING 티켓 수량 0 이하·모드별 필드 혼용·좌석 배치 오류 `400`(INVALID_SEAT_LAYOUT) / 종료일시가 시작일시보다 빠름 `400`(INVALID_PERIOD) / 운영 시간 역전 `400`(INVALID_OPERATING_HOURS) / 좌표 한쪽만 입력 `400`(INVALID_COORDINATES) / 판매 기간·티켓 날짜 오류 `400`(INVALID_TICKET_SALE_PERIOD·INVALID_TICKET_DATE) / 본문 이미지 개수 초과 `400`(INVALID_DETAIL_IMAGE_COUNT) / 주최자 Role 없음 `403`(FORBIDDEN_HOST_ROLE) / 인증 없음 `401` |
| 보안 | HOST 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### listPendingFestivals — `GET /api/admin/festivals`

운영자가 페스티벌 심사 목록을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 5 / 시나리오 5 |
| Request | Query: status(기본 ALL, PENDING은 PENDING·PUBLISH_PENDING, APPROVED는 PUBLISHED·CLOSED·CANCELLATION_PENDING·CANCELLED, REJECTED), keyword(페스티벌명 또는 주최자 닉네임·이메일), page·size(기본 10, createdAt 내림차순) |
| 정상 | `200` — 상태 묶음별 페스티벌 목록 + `meta.pagination`. 주최자 키워드 검색은 인증 서비스 `GET /internal/v1/users/search`를 쓰고 실패하면 주최자 매칭 없이 페스티벌명으로만 찾는다 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (상태 필터·검색·페이징은 이후 확장) |

### reviewFestival — `PATCH /api/admin/festivals/{id}`

운영자가 심사 대기 페스티벌을 공개·반려한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 5 / 시나리오 5, 15 |
| Request | Path: id / Body: `decision`(`PUBLISHED` 또는 `REJECTED`), rejectReason(반려 시 필수) |
| 정상 | `200` — 상태 전이 후 Festival 반환. 공개는 PENDING → PUBLISH_PENDING을 먼저 저장하고 SEATED 티켓종류마다 generateSeats(`POST /internal/v1/seats`)를 호출한 뒤 PUBLISHED(SEATED가 없으면 호출 없이 바로 PUBLISHED). 좌석 생성 호출이 실패하면 PUBLISH_PENDING인 채 200으로 반환하고 FestivalPublishRetryScheduler(60초마다, 30초 이상 머문 건)가 재시도. 반려는 REJECTED + 사유 저장 |
| 실패 | 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) / 존재하지 않음 `404`(FESTIVAL_NOT_FOUND) / decision이 PUBLISHED·REJECTED가 아니면 `400`(`INVALID_DECISION`) / PENDING이 아닌(이미 심사된·PUBLISH_PENDING) 것이면 `409`(`ALREADY_REVIEWED`) / 반려 사유 없음 `400`(REJECT_REASON_REQUIRED) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (PUBLISH_PENDING·좌석 생성 연동은 Sprint 3) |

### listMyFestivals — `GET /api/host/festivals`

주최자가 본인이 등록한 페스티벌 목록을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 5 / 시나리오 5 |
| Request | - |
| 정상 | `200` — 본인이 등록한 페스티벌 목록(상태 무관) |
| 실패 | 인증 없음 `401` / 주최자 Role 없음 `403`(FORBIDDEN_HOST_ROLE) |
| 보안 | HOST 권한(본인) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### getMyFestivalDetail — `GET /api/host/festivals/{id}`

주최자가 본인 페스티벌의 상세 정보를 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 5 / 시나리오 5 |
| Request | Path: id |
| 정상 | `200` — 본인 소유 페스티벌 상세 |
| 실패 | 타인 소유 페스티벌 조회 시도 `403`(FORBIDDEN_NOT_OWNER)(본인 소유 페스티벌만 관리 규칙) / 존재하지 않는 페스티벌 `404`(FESTIVAL_NOT_FOUND) / 주최자 Role 없음 `403` / 인증 없음 `401` |
| 보안 | HOST 권한(본인) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### createReservation — `POST /api/reservations`

참가자가 원하는 티켓 종류·수량(SEATED는 좌석)으로 예매를 신청한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | Story 6 / 시나리오 6 (SEATED 좌석 직접 선택은 시나리오 15) |
| Request | Body: festivalId, ticketTypeId, STANDING이면 quantity(1 이상), SEATED면 seatIds(필수, 선택한 좌석 수가 곧 수량) |
| 정상 | `201` — PENDING 상태 예매 생성, expiresAt = 지금 + 10분(결제 대기). 순서: 페스티벌 공개 조회(`GET /api/festivals/{id}`)로 PUBLISHED·티켓종류·판매 기간 확인 → 1인 구매 한도 검사((사용자, 페스티벌) 잠금 행을 잡은 뒤 합산) → STANDING은 festival 재고 원자적 차감, SEATED는 좌석마다 AVAILABLE→HELD 조건부 전환(10분) 후 STOMP로 HELD 알림 + 표시용 잔여 수량 차감 → 예매 저장. 저장이 실패하면 차감한 재고를 복구 호출로 되돌린다 |
| 실패 | 재고 초과 `409`(STOCK_EXCEEDED) / 1인당 구매 한도 초과 `409`(PURCHASE_LIMIT_EXCEEDED) — 한도는 페스티벌당 합산(티켓 종류를 나눠 사도 합산), 기본 4장(환경변수 RESERVATION_MAX_QUANTITY_PER_FESTIVAL), PENDING·CONFIRMED·PARTIALLY_REFUNDED의 남은 장수로 계산 / 판매 전·판매 종료 `409`(TICKET_SALE_NOT_STARTED·TICKET_SALE_ENDED) / 이미 HELD·SOLD인 좌석 포함 `409`(SEAT_ALREADY_TAKEN) / 모드에 맞지 않는 요청(SEATED인데 seatIds 없음, STANDING인데 quantity 없음) `400`(INVALID_SEAT_REQUEST) / 다른 티켓종류의 좌석 `404`(SEAT_NOT_FOUND) / 비공개·종료·미존재 페스티벌 `404`(FESTIVAL_NOT_PUBLISHED) / 존재하지 않는 티켓종류 `404`(TICKET_TYPE_NOT_FOUND) / 페스티벌 서비스 응답 실패·Timeout `503`(FESTIVAL_SERVICE_UNAVAILABLE) / 인증 없음 `401`(예매 신청 재고 초과 금지, 동시 요청에도 재고 초과 없음 규칙) |
| 보안 | 로그인 필요. 서비스 코드는 Role을 검사하지 않아 USER·HOST·ADMIN·STOREHOST 모두 예매할 수 있다(의도된 동작). HELPER 토큰은 Gateway가 `403`으로 차단 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (SEATED 좌석 선택·좌석 맵 조회·좌석 생성 내부 API는 Sprint 3 확장 — 좌석 맵 `SeatController`, 좌석 생성 `InternalSeatController`) |

## 외부 HTTP 계약 — Sprint 2

### getMyReservations — `GET /api/reservations/me`

참가자가 본인의 예매 내역을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | Story 6 / 시나리오 6 |
| Request | - |
| 정상 | `200` — 본인 예매 내역 목록 |
| 실패 | 인증 없음 `401` (X-User-Id로 본인 목록만 조회하므로 타인 예매를 요청할 경로가 없다. 타인 예매 단건 조회 `GET /api/reservations/{id}`는 `403`(FORBIDDEN_NOT_OWNER)) |
| 보안 | 로그인 필요(본인). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 |

### listFestivals — `GET /api/festivals`

공개된 페스티벌 목록을 검색·필터링해서 보여준다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 1 / 시나리오 1 |
| Request | Query: page, size, sort (Spring Pageable, 인기순은 `sort=viewCount,desc`). date/region/category 서버 필터는 미구현 — 프론트가 한 번에 많이 받아와 클라이언트에서 필터링(홈·전체 목록 모두 size=100) |
| 정상 | `200` — 공개(PUBLISHED) 상태 페스티벌 목록 + `meta.pagination`(종료·취소된 행사는 목록에서 제외). 매진 여부는 `ticketType.remainQuantity`로 프론트가 판단 |
| 실패 | (해당 없음) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 |

### getFestivalDetail — `GET /api/festivals/{id}`

특정 페스티벌의 상세 정보(일정·장소·가격·재고)를 보여준다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 1 / 시나리오 1 |
| Request | Path: id |
| 정상 | `200` — 일정·장소·가격·남은수량 포함 상세. 호출 시 조회수를 집계한다(같은 IP 24시간 1회, IP는 해시로만 저장, 인기순 정렬의 근거). 좌표가 비어 있으면 이때 한 번 채운다(실패해도 상세는 성공) |
| 실패 | 미승인·반려·미존재 페스티벌 조회 시 `404`(FESTIVAL_NOT_FOUND) — PUBLISHED·CLOSED·CANCELLATION_PENDING·CANCELLED만 200. 미승인 페스티벌은 존재 자체를 숨기려고 404로 응답한다(코드 주석) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 |

## 외부 HTTP 계약 — Sprint 3

### cancelReservation — `PATCH /api/reservations/{id}/cancel`

참가자가 결제 전(PENDING) 예매를 직접 취소한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | Story 9(취소·환불) |
| Request | Path: id |
| 정상 | `200` — 본인 소유·PENDING 예매를 CANCELLED(USER_CANCELLED)로 바꾸고 재고 복구(SEATED는 좌석을 AVAILABLE로 되돌리고 STOMP로 알린 뒤 되돌린 좌석 수만큼 잔여 수량 복구). 결제가 끝난 예매의 취소는 환불 API(`POST /api/payments/{paymentId}/cancellations`)로 한다 |
| 실패 | 인증 없음 `401` / 타인 소유 `403`(FORBIDDEN_NOT_OWNER) / 없는 예매 `404`(RESERVATION_NOT_FOUND) / PENDING이 아님 `409`(RESERVATION_NOT_CANCELLABLE) |
| 보안 | 로그인 필요(본인). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |

### getReservationQr — `GET /api/reservations/{id}/qr`

참가자가 입장용 QR·입장 코드를 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | QR 발급·현장 입장 검증 |
| Request | Path: id |
| 정상 | `200` — QR 이미지 URL(qrImageUrl, 외부 QR 이미지 생성 API 주소)·QR 원본값(qrToken)·입장코드(checkInCode)·입장처리시각(checkedInAt) 반환. QR은 결제확정(confirm) 시점에 함께 발급되고, 입장 후에도 계속 조회 가능(재사용 방지는 서버가 checkedInAt으로 판단). 부분 환불(PARTIALLY_REFUNDED)된 예매도 남은 장수가 있으면 조회 가능 |
| 실패 | 인증 없음 `401` / 타인 소유 `403`(FORBIDDEN_NOT_OWNER) / 없는 예매 `404` / 결제 미확정·남은 장수 없음 `409`(RESERVATION_NOT_CONFIRMED) |
| 보안 | 로그인 필요(본인) |
| 상태 | IMPLEMENTED |

### preparePayment — `POST /api/payments/prepare`

결제창을 열기 전에 서버에 결제 건을 만든다

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | Story 7(결제) |
| Request | Body: reservationId |
| 정상 | `201` — 예약 서비스 getReservationForPayment로 예매를 조회해 본인·PENDING을 확인한 뒤 Payment를 READY로 저장하고, PortOne Browser SDK에 그대로 넘길 storeId·channelKey·paymentId·totalAmount 반환(금액은 예매 금액 스냅샷, 구매자 추가 수수료 없음) |
| 실패 | 인증 없음 `401` / 타인 소유 예매 `403`(FORBIDDEN_RESERVATION_OWNER) / 없는 예매 `404`(RESERVATION_NOT_FOUND) / 결제 불가 상태(PENDING 아님) `409`(RESERVATION_NOT_PAYABLE) / 예약 서비스 응답 실패·Timeout `503`(RESERVATION_SERVICE_UNAVAILABLE) |
| 보안 | 로그인 필요(본인). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |

### completePayment — `POST /api/payments/{paymentId}/complete`

결제창 결과를 서버가 PortOne 재조회로 확정한다

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | Story 7(결제) |
| Request | Path: paymentId (본문 없음 — 프론트가 보낸 결제 상태·금액은 받지 않는다) |
| 정상 | `200` — PortOne 서버 재조회로 상점·채널·테스트 채널 여부·통화·금액을 우리 결제와 대조한 뒤 결제 상태 반환. PAID면 예매도 CONFIRMED로 확정(QR·입장코드 함께 발급), FAILED면 예매 취소(PAYMENT_FAILED), 가상계좌 발급이면 입금 기한까지 예매 홀드 연장. 이미 확정된 결제를 다시 부르면 현재 상태를 그대로 반환(멱등). 모바일(리다이렉트 결제)에서는 결제 후 `/payments/redirect` 복귀 화면이 같은 API로 최종 확인한다(같은 탭에서 시작한 결제만 15분 안에 복구) |
| 실패 | 인증 없음 `401` / 타인 결제 `403`(FORBIDDEN_PAYMENT_OWNER) / 없는 결제 `404`(PAYMENT_NOT_FOUND) / PortOne 조회 결과와 불일치 `409`(PAYMENT_VERIFICATION_FAILED) / PortOne이 아직 모르는 결제(결제창 완료 전 호출) `409`(PAYMENT_NOT_YET_PROCESSED) / 이미 만료·취소됐거나 다른 결제로 확정된 예매에 결제 확정 시도 `409`(RESERVATION_ALREADY_FINALIZED) — 이때 결제에 확정 거절을 기록하고 결제별 고정 멱등키로 자동 전액 환불(보상)하며, 환불이 실패하면 PaymentCompensationScheduler(60초)가 같은 키로 재시도한다(아키텍처 문서 결정 기록 참고). 예약 서비스 확정 호출이 Timeout이면 결제는 PAID·예매 확정 시각 없음으로 남고, 완료 API 재호출이나 웹훅 재처리 때 다시 확정한다 |
| 보안 | 로그인 필요(본인 결제) |
| 상태 | IMPLEMENTED |

### receiveWebhook — `POST /api/v1/webhooks/portone`

PortOne 결제·취소 알림을 받아 결제 상태를 맞춘다

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | Story 7(웹훅 수신·서명 검증, Task 7-5) |
| Request | PortOne 원문 Body + webhook-id·webhook-signature·webhook-timestamp 헤더 |
| 정상 | `200`(본문 없음) — 서명 검증 후 webhook_id로 이벤트를 기록하고 completePayment와 같은 동기화 로직으로 결제 상태 반영(카드결제 완료, 가상계좌 입금 확인 등), 이어서 취소 대사(결제 후 취소·대시보드 취소 반영)를 돈다. 처리 중 일시 장애(예약 서비스 응답 실패, Timeout 등)가 나도 200으로 응답하고 이벤트를 FAILED로 남겨 WebhookRetryScheduler(60초)가 최대 30회 재처리, 다시 해도 결과가 같은 오류(다른 팀 결제 등)는 IGNORED로 기록 |
| 실패 | 서명 검증 실패 `400`(본문 없음) |
| 보안 | 사용자 인증 없음, PortOne 서명으로 대체. webhook_id unique 제약으로 재전송 시 한 번만 처리(멱등, 이전에 FAILED였던 이벤트만 재전송을 계기로 다시 처리) |
| 상태 | IMPLEMENTED |

### verifyReservation / verifyReservationByCode — `POST /api/organizer/reservations/verify`, `POST /api/organizer/reservations/verify-code`

현장에서 QR 또는 입장 코드로 입장을 처리한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | QR 발급·현장 입장 검증 |
| Request | Body: qrToken(verify) 또는 checkInCode(verify-code, 앞뒤 공백 제거·대문자로 정규화) |
| 정상 | `200` — 결제확정(CONFIRMED)·부분 환불(PARTIALLY_REFUNDED) 상태·미입장 예매를 입장 처리(checkedInAt 기록). 입장은 예매 단위라 한 번 스캔으로 남은 장수 전체가 입장 처리되고, 조건부 UPDATE로 동시 스캔도 한 번만 성공 |
| 실패 | 인증 없음 `401` / HOST·HELPER가 아니거나 HOST가 본인 주최가 아닌 페스티벌 `403`(FORBIDDEN_NOT_ORGANIZER) / 담당 페스티벌이 없는 HELPER `403`(HELPER_FESTIVAL_NOT_ASSIGNED) / HELPER가 담당 외 페스티벌 티켓을 스캔 `409`(OTHER_FESTIVAL_TICKET) / 존재하지 않는 QR·코드 `404`(INVALID_QR_TOKEN·INVALID_CHECK_IN_CODE) / 결제 미확정 `409`(RESERVATION_NOT_CONFIRMED) / 공연 시작 전 `409`(FESTIVAL_NOT_STARTED, 종료 후 입장은 허용) / 이미 입장 처리됨 `409`(ALREADY_CHECKED_IN) |
| 보안 | HOST(본인 소유 페스티벌) 또는 HELPER(JWT에 배정된 페스티벌, Gateway가 X-Festival-Id로 전달하고 요청마다 세션 확인) |
| 상태 | IMPLEMENTED |

### getCheckInStats — `GET /api/organizer/reservations/check-in-stats`

현장 입장 현황을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | QR 발급·현장 입장 검증 |
| Request | Query: festivalId |
| 정상 | `200` — 해당 페스티벌 총 티켓 수(환불분 제외한 남은 장수 합) 대비 현재 입장 인원 |
| 실패 | 인증 없음 `401` / 권한 없음(HOST·HELPER 아님, HOST 타페스티벌) `403`(FORBIDDEN_NOT_ORGANIZER) / HELPER 배정 외 페스티벌 `409`(OTHER_FESTIVAL_TICKET) |
| 보안 | HOST 또는 HELPER, verifyReservation과 동일한 소유권 검증 |
| 상태 | IMPLEMENTED |

### uploadFestivalImages — `POST /api/host/festivals/images`

주최자가 페스티벌 이미지를 먼저 올려 URL을 받는다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 페스티벌 이미지 등록 |
| Request | multipart: thumbnail(파일 최대 1개, 대표 이미지) + detailImages(파일 최대 2개, 본문 이미지). 파일당 10MB, 요청당 35MB |
| 정상 | `200` — thumbnailImageUrl, detailImageUrls 반환, 페스티벌 등록 요청 Body에 그대로 실어 보냄. 파일은 서버 디스크에 저장되고 `/api/festivals/images/**` 경로로 제공 |
| 실패 | 대표 1장·본문 2장 초과 `400`(INVALID_IMAGE_COUNT·INVALID_DETAIL_IMAGE_COUNT) / 파일당 10MB 초과 `400`(INVALID_IMAGE_SIZE) / 이미지 파일 아님(확장자 허용 목록과 실제 디코딩으로 확인) `400`(INVALID_IMAGE_TYPE) / 주최자 Role 없음 `403`(FORBIDDEN_HOST_ROLE) |
| 보안 | HOST 권한 |
| 상태 | IMPLEMENTED |

### createHelperAccount / listHelperAccounts — `POST /api/host/festivals/{festivalId}/helpers`, `GET /api/host/festivals/{festivalId}/helpers`

주최자가 현장 도우미를 이메일로 초대하고 목록을 본다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service(내부적으로 auth-service에 계정 생성·조회 위임) |
| 관련 Story·시나리오 | 도우미 계정 발급 |
| Request | Path: festivalId / 초대는 Body: email |
| 정상 | 초대 `201` — auth-service에 활성화 전(PENDING_ACTIVATION) 도우미 계정을 만들고 연락 이메일로 초대 링크 발송(도우미가 링크에서 직접 비밀번호를 정해 활성화, 응답·서버 어디에도 평문 비밀번호 없음) / 목록 `200` — 도우미 계정과 초대·발송 상태 |
| 실패 | 인증 없음 `401` / HOST 아님 `403`(FORBIDDEN_HOST_ROLE) / 본인 소유 페스티벌 아님 `403`(FORBIDDEN_NOT_OWNER) / 페스티벌 없음 `404` / 같은 행사에 같은 이메일 중복 초대 `409`(INVITATION_DUPLICATE) / 종료된 행사 `410`(HELPER_FESTIVAL_ENDED) / 발송 60초 쿨다운·24시간 10회 초과 `429` / 메일 발송 실패 `502`(INVITATION_SEND_FAILED, 계정은 보존) / 인증 서비스 응답 실패 `503`(AUTH_SERVICE_UNAVAILABLE) |
| 보안 | HOST 권한(본인 소유 페스티벌만) |
| 상태 | IMPLEMENTED |

### listBoothsForFestival — `GET /api/festivals/{festivalId}/booths`

페스티벌 상세의 부스 목록을 조회한다(WAITING 숨김)

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 14 / 시나리오13 |
| Request | Path: festivalId |
| 정상 | `200` — WAITING을 제외한 부스 목록(OPEN·CLOSED) |
| 실패 | (해당 없음) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |

### getBoothDetail — `GET /api/booths/{id}`

부스 단건 상세를 조회한다(WAITING이면 404)

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 14 / 시나리오13 |
| Request | Path: id |
| 정상 | `200` — 부스 상세(OPEN·CLOSED만) |
| 실패 | WAITING·미존재 `404`(BOOTH_NOT_FOUND) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |

### createBooth — `POST /api/store/booths`

STOREHOST가 페스티벌에 부스를 개설한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 14 |
| Request | Body: festivalId, title, description, boothHostName, imageUrl(선택) |
| 정상 | `201` — WAITING 상태로 부스 생성 |
| 실패 | 인증 없음 `401` / STOREHOST 아님 `403`(FORBIDDEN_STOREHOST_ROLE) / 존재하지 않는 페스티벌 `404` / 페스티벌에 이미 부스가 있음 `409`(DUPLICATE_BOOTH_FOR_FESTIVAL, 페스티벌당 1개) |
| 보안 | STOREHOST 권한(소유권 검증 없음 — 역할 체크만, 페스티벌 소유·상태도 검증하지 않음) |
| 상태 | IMPLEMENTED |

### uploadBoothImage — `POST /api/store/booths/images`

STOREHOST가 부스 대표 이미지를 업로드한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 14 |
| Request | multipart: image(파일 최대 1개) |
| 정상 | `200` — imageUrl 반환 |
| 실패 | 10MB 초과 `400`(INVALID_IMAGE_SIZE) / 이미지 아님 `400`(INVALID_IMAGE_TYPE) / STOREHOST 아님 `403` |
| 보안 | STOREHOST 권한 |
| 상태 | IMPLEMENTED |

### listMyBooths / getMyBoothDetail / changeBoothStatus — `GET /api/store/booths`, `GET /api/store/booths/{id}`, `PATCH /api/store/booths/{id}/status`

STOREHOST가 본인이 개설한 부스 목록·상세를 조회하고 상태를 변경한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | Story 14 |
| Request | Path: (상세·상태변경은) id / Body: boothStatus(WAITING/OPEN/CLOSED) |
| 정상 | `200` — 목록·상세는 상태 무관(WAITING도 보임), 상태변경은 변경 후 부스 반환(세 상태 사이 자유 전이) |
| 실패 | 인증 없음 `401` / STOREHOST 아님 `403` / 타인 소유 `403`(FORBIDDEN_NOT_OWNER) / 미존재 `404` |
| 보안 | STOREHOST 권한(본인 소유만) |
| 상태 | IMPLEMENTED |

### requestBoothWaitlist / getMyBoothWaitlist — `POST /api/booth-waitlists/{boothId}`, `GET /api/booth-waitlists/{boothId}/me`

참가자가 부스에 선착순 대기 신청을 하고 본인 순번을 조회한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | Story 14 / 시나리오13 |
| Request | Path: boothId |
| 정상 | 신청 `201` — 선착순 대기번호(queueNumber) 발급 / 내 순번 조회 `200` |
| 실패 | 인증 없음 `401` / 해당 페스티벌 티켓(CONFIRMED·PARTIALLY_REFUNDED 예매) 미보유 `403`(TICKET_NOT_FOUND) / 부스 미존재·WAITING `404`(BOOTH_NOT_FOUND) / 신청 내역 없음 `404`(WAITLIST_NOT_FOUND) / 마감 부스 `409`(BOOTH_NOT_OPEN) / 중복 신청 `409`(ALREADY_REQUESTED) / 페스티벌 서비스 응답 실패 `503`(FESTIVAL_SERVICE_UNAVAILABLE) |
| 보안 | 로그인 필요(본인). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |

### getChatbotRecommendation — `POST /api/chatbot/recommendations`

참가자가 AI 챗봇에게 조건을 말해 페스티벌 추천을 받는다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오 14 |
| Request | Body: message(최대 500자), history(직전 대화 최대 10개, role/content 배열) |
| 정상 | `200` — 공개(PUBLISHED) 페스티벌 중 최대 3개 추천(reply, recommendations당 festivalId·링크·이유). Gemini가 후보에 없는 festivalId를 말하면 서버가 필터링해 제외 |
| 실패 | 인증 없음 `401` / 메시지 유효성 실패 `400` / Gemini 호출 실패·한도초과·파싱실패 `503`(CHATBOT_UNAVAILABLE) |
| 보안 | 로그인 필요(서비스 코드는 Role을 검사하지 않음, HELPER는 Gateway `403`), 대화는 서버에 저장하지 않음 |
| 상태 | IMPLEMENTED |

### getQueueStatus / callNext — `GET /api/booth-waitlists/booths/{boothId}/queue-status`, `POST /api/booth-waitlists/booths/{boothId}/call-next`

STOREHOST가 본인 부스의 대기열 현황을 조회하고 다음 순번을 호출한다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | 시나리오 16 |
| Request | Path: boothId |
| 정상 | 조회 `200` — calledNumber, issuedNumber, waitingCount / 호출 `200` — 호출 후 같은 형태로 반환 |
| 실패 | 인증 없음 `401` / STOREHOST 아님 `403`(FORBIDDEN_STOREHOST_ROLE) / 본인 소유 부스 아님 `403`(FORBIDDEN_NOT_OWNER) / 대기자가 없을 때 호출 시도 `409`(NO_WAITING_QUEUE) |
| 보안 | STOREHOST 권한(본인 소유 부스만) — 소유권은 festival-service의 `GET /api/store/booths/{id}`를 요청자 헤더 그대로 전달해 확인 |
| 상태 | IMPLEMENTED |

### getMyActiveWaitlists — `GET /api/booth-waitlists/me/active`

참가자가 본인이 신청한 모든 부스의 대기 현황을 한 번에 조회한다(챗봇 알림 폴링용)

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | 시나리오 16 |
| Request | - |
| 정상 | `200` — 본인 신청 내역 배열, 항목당 boothId, festivalId, queueNumber, calledNumber, myTurn |
| 실패 | 인증 없음 `401` |
| 보안 | 로그인 필요(본인) |
| 상태 | IMPLEMENTED |

## 외부 HTTP 계약 — Week 4 안정화 추가분 (2026-09-28 점검, 소급 반영)

### generateAiDraft — `POST /api/host/festivals/ai-draft`

호스트가 등록 폼 초안을 AI로 생성한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오14 연장선(Gemini 활용) |
| Request | Body: prompt(필수, 500자 이내 자유 텍스트) |
| 정상 | `200` — description · startDate/endDate(유추 가능할 때만) · locationQuery(장소가 언급됐을 때만, 지도 검색용 키워드일 뿐 좌표는 직접 만들지 않음) · ticketTypeSuggestions(최대 3개) 반환. 프론트가 이 값을 폼에 채우고, locationQuery가 있으면 카카오 장소 검색으로 좌표·지역까지 채운다(주최자가 등록 전 전부 검토·수정, 자동 제출 없음) |
| 실패 | HOST 아님 `403`(FORBIDDEN_HOST_ROLE) / prompt 누락·500자 초과 `400`(INVALID_REQUEST) / Gemini 호출·응답 파싱 실패 `503`(CHATBOT_UNAVAILABLE) |
| 보안 | HOST 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Week 4 안정화 (2026-09-22 추가) |

### backfillCoordinates — `POST /api/admin/festivals/backfill-coordinates`

운영자가 좌표 없는 페스티벌의 좌표를 일괄로 채운다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오1 보강(지도 표시) |
| Request | 없음 |
| 정상 | `200` — 좌표가 null인 페스티벌 전체를 대상으로 카카오 키워드 장소 검색을 호출해 지역이 일치하는 결과만 적용, 건별 적용/건너뜀 사유 목록 반환. 실행할 때마다 남은 null 좌표만 대상이라 여러 번 실행해도 안전(멱등) |
| 실패 | 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED — 참고: 방문자가 상세 페이지(`GET /api/festivals/{id}`)를 볼 때도 좌표가 없으면 같은 로직으로 그 자리에서 한 번 채우는 지연 백필이 있다(`FestivalCoordinateBackfillService`의 backfillIfMissing, 실패해도 상세 조회 자체는 막지 않음). 이 관리자 API는 방문이 뜸한 페스티벌 좌표를 수동으로 앞당겨 채울 때 쓴다 |
| 추가·변경 Sprint | Week 4 안정화 (2026-09-23 추가) |

## 외부 HTTP 계약 — 핵심 흐름 추가 상세 (2026-09-28 코드 기준 소급 작성)

아래 API는 이전 점검에서 목록으로만 남아 있던 핵심 흐름(인증·환불·행사 취소·정산)입니다. 팀이 붙인 이름이 없어 `METHOD /path`로만 식별하며, 추가·변경 Sprint는 해당 코드가 처음 들어간 커밋 날짜로 판단했습니다.

### `POST /api/auth/logout`

현재 기기의 Refresh Token을 폐기하고 쿠키를 지운다

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Cookie: refreshToken(선택, 본문 없음) |
| 정상 | `200` — 쿠키의 Refresh Token 해시와 일치하는 행에 폐기 시각을 기록하고 refreshToken 쿠키를 삭제. 쿠키가 없거나 DB에 없는 토큰이어도 `200`(이미 로그아웃 상태로 취급). Access Token은 서버에 저장하지 않으므로 만료 전까지 유효 |
| 실패 | (해당 없음) |
| 보안 | 인증 불필요(공개 라우트) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-07 추가) |

### `POST /api/auth/email/send`

회원가입·비밀번호 재설정 전 이메일로 6자리 인증코드를 보낸다

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Body: email |
| 정상 | `200` — 6자리 코드(유효 5분)를 저장하고 메일을 비동기로 발송 |
| 실패 | 이메일 형식 오류 `400`(VALIDATION_ERROR) / 재발송 30초 쿨다운 `429`(TOO_MANY_REQUESTS_COOLDOWN) / 10분에 5회 초과 `429`(TOO_MANY_REQUESTS_LIMIT) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-08 추가) |

### `POST /api/auth/email/verify`

받은 인증코드를 확인하고 인증 토큰을 받는다

| 항목 | 정의 |
| --- | --- |
| Owner | Auth-Service |
| 관련 Story·시나리오 | Story 2 / 시나리오 2 |
| Request | Body: email, code |
| 정상 | `200` — 인증 완료 기록 + verificationToken 반환(DB에는 해시만 저장). 회원가입은 인증 완료 기록만 확인하고, 비밀번호 재설정(`POST /api/auth/reset-password`)은 이 토큰이 맞고 인증 후 10분 안일 때만 허용 |
| 실패 | 코드 불일치·발송 기록 없음 `400`(INVALID_VERIFICATION_CODE, 틀린 횟수 누적) / 만료 `400`(VERIFICATION_CODE_EXPIRED) / 5회 오답 후 `429`(TOO_MANY_VERIFY_ATTEMPTS — 코드를 새로 받아야 함) |
| 보안 | 인증 불필요 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-08 추가) |

### `GET /api/reservations/{id}/refund-quote`

참가자가 환불 전 위약금과 돌려받을 금액을 미리 본다

| 항목 | 정의 |
| --- | --- |
| Owner | Reservation-Service |
| 관련 Story·시나리오 | Story 9(취소·환불) |
| Request | Path: id / Query: quantity(선택 — 없으면 남은 전량) |
| 정상 | `200` — refundable, rejectReason, refundQuantity, feePercent, grossAmount, feeAmount, refundAmount 등. 환불 불가도 예외가 아니라 refundable=false와 rejectReason 문자열로 돌려준다: 결제 확정 전·이미 전액 환불(RESERVATION_NOT_REFUNDABLE), 행사 취소 진행·완료(FESTIVAL_CANCELLATION_REFUND_PENDING — 운영자 승인 후 위약금 없이 전액 환불되므로 본인 환불을 막음), 입장 완료(ALREADY_CHECKED_IN_NOT_REFUNDABLE), 수량 초과(REFUND_QUANTITY_EXCEEDED), 공연 시작 24시간 전 이후(REFUND_WINDOW_CLOSED, 환경변수 REFUND_CUTOFF_HOURS). 위약금은 공연 시작까지 남은 기간으로 정한다 — 10일 이상 0%, 7일 이상 10%, 3일 이상 20%, 1일 이상 30%(설정값 `refund.tiers`) |
| 실패 | 인증 없음 `401` / 타인 소유 `403`(FORBIDDEN_NOT_OWNER) / 없는 예매 `404`(RESERVATION_NOT_FOUND) / 페스티벌 조회 실패 `503`(FESTIVAL_SERVICE_UNAVAILABLE) |
| 보안 | 로그인 필요(본인). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-09 추가) |

### `POST /api/payments/{paymentId}/cancellations`

참가자가 결제를 전체·부분 환불한다

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | Story 9(취소·환불) |
| Request | Path: paymentId / Header: `Idempotency-Key`(선택) / Body(선택): quantity(1 이상, 없으면 남은 전량), reason. 금액은 받지 않는다 |
| 정상 | `200` — 순서: 같은 키의 이전 요청이 있으면 PortOne 재조회로 맞춘 기존 결과 반환 → 예약 서비스 환불 견적(`GET /internal/v1/reservations/{id}/refund-quote`)으로 금액 결정 → 취소 기록을 REQUESTED로 먼저 저장 → PortOne 취소(같은 키 전달) → PortOne 재조회로 전체 취소 목록 대사 → PG 취소가 SUCCEEDED로 확정된 건만 예약 서비스에 환불 반영(`PATCH /internal/v1/reservations/{id}/refund`). 예매는 PARTIALLY_REFUNDED 또는 REFUNDED가 되고, 환불된 재고·좌석은 즉시 풀리지 않고 매일 19시(환경변수 REFUND_STOCK_RELEASE_HOUR) 이후 일괄 반환 |
| 실패 | 인증 없음 `401` / 타인 결제·다른 결제에 쓴 키 `403`(FORBIDDEN_PAYMENT_OWNER) / 없는 결제 `404`(PAYMENT_NOT_FOUND) / 취소할 수 없는 결제 상태 `409`(PAYMENT_NOT_CANCELLABLE) / 견적 거절 `409`(REFUND_WINDOW_CLOSED·ALREADY_CHECKED_IN_NOT_REFUNDABLE·REFUND_QUANTITY_EXCEEDED, 그 밖의 거절 사유는 REFUND_NOT_ALLOWED) / quantity 0 이하 `400`(INVALID_REQUEST) / 예약 서비스 견적 조회 실패 `503`(RESERVATION_SERVICE_UNAVAILABLE) / PortOne 취소 호출 실패 `502`(REFUND_FAILED) — 이때 취소 기록은 REQUESTED로 남아 이후 재조회 대사에서 결과를 맞춘다 |
| 보안 | 로그인 필요(본인 결제). HELPER는 Gateway `403` |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-09 추가) |

### `POST /api/host/festivals/{id}/cancellation-request`

주최자가 시작 전 공개 행사의 취소(주최자 귀책)를 요청한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request | Path: id / Body: reason(필수, 500자 이내) |
| 정상 | `200` — PUBLISHED이고 시작 전인 행사를 CANCELLATION_PENDING으로 바꾸고 이전 상태를 보관. 이미 CANCELLATION_PENDING·CANCELLED면 현재 상태를 그대로 반환(재요청 멱등) |
| 실패 | 인증 없음 `401` / HOST 아님 `403`(FORBIDDEN_HOST_ROLE) / 없는 행사 또는 타인 소유 행사 `404`(FESTIVAL_NOT_FOUND — 남의 행사는 존재 여부를 알리지 않으려고 403 대신 404, 코드 주석) / PUBLISHED가 아님 `409`(FESTIVAL_NOT_CANCELLABLE) / 이미 시작한 행사 `409`(FESTIVAL_ALREADY_STARTED, 2026-09-22 팀 결정) / 사유 누락·500자 초과 `400` |
| 보안 | HOST 권한(본인 소유 행사) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가, 시작 전 제한은 Week 4 안정화) |

### `GET /api/admin/festivals/cancellation-requests`

운영자가 행사 취소 요청 목록을 본다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request | Query: status(기본 PENDING, 그 밖에 REFUNDING·CANCELLED·REJECTED) |
| 정상 | `200` — 상태별 취소 목록. PENDING 목록에는 승인 전 영향 미리보기(판매 티켓 수·예상 환불 금액)를 결제 서비스 `GET /internal/v1/payments/refund-preview`로 붙이고, 그 조회가 실패하면 금액 없이 목록만 반환 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가, 영향 미리보기는 Week 4 안정화) |

### `POST /api/admin/festivals/{id}/approve-cancellation`

운영자가 행사 취소를 승인해 전액 환불을 시작시킨다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request | Path: id |
| 정상 | `200` — 승인 시각·승인자를 최초 1회만 기록(재요청해도 근거 불변), 상태는 CANCELLATION_PENDING 유지. 이후 결제 서비스 FestivalRefundScheduler(60초)가 `GET /internal/v1/festivals/refund-candidates`로 이 행사를 가져가 결제별로 위약금 없이 전액 환불하고, 모든 예매의 환불 반영이 끝나면 `POST /internal/v1/festivals/{id}/complete-cancellation`으로 CANCELLED 전환 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) / 없는 행사 `404` / 취소 요청 상태가 아님 `409`(CANCELLATION_NOT_REQUESTED) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

### `POST /api/admin/festivals/{id}/reject-cancellation`

운영자가 승인 전 행사 취소 요청을 반려한다

| 항목 | 정의 |
| --- | --- |
| Owner | Festival-Service |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request | Path: id |
| 정상 | `200` — 요청 기록을 반려 이력(`festival_cancellation_rejections`)으로 복사한 뒤 행사를 요청 전 상태(공개·종료)로 되돌림 |
| 실패 | 인증 없음 `401` / 비운영자 `403`(FORBIDDEN_ADMIN_ROLE) / 없는 행사 `404` / 취소 요청 상태가 아님 `409`(CANCELLATION_NOT_REQUESTED) / 이미 승인돼 환불이 진행 중 `409`(CANCELLATION_ALREADY_APPROVED) |
| 보안 | ADMIN 권한 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Week 4 안정화 (2026-09-22 추가) |

### 정산 조회 — `GET /api/admin/settlements`, `GET /api/host/settlements`, `GET /api/admin/settlements/summary`, `GET /api/host/settlements/summary`, `GET /api/admin/settlements/{id}`, `GET /api/host/settlements/{id}`

운영자는 전체, 주최자는 본인 행사의 정산 목록·합계·상세를 본다(한 컨트롤러가 `/api/{admin|host}/settlements`로 두 경로를 받는다)

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | 시나리오 11(정산) |
| Request | Query(목록·합계): from, to(Instant), status, festivalId, hostUserId, paymentMethod, festivalName, hostName, testPayment(기본 false), dateBasis(기본 SETTLEMENT_AT), 목록만 page·size(기본 20) / Path(상세): id |
| 정상 | `200` — 목록은 `meta.pagination` 포함, 합계는 금액 합계, 상세는 정산 라인(결제별 금액·수수료·환입)과 보류 사유. 정산은 행사 종료 24시간 뒤부터 매일 02:00(Asia/Seoul) 배치가 계산한다 |
| 실패 | 인증 없음 `401` / admin 경로에 ADMIN이 아님, host 경로에 HOST가 아님 `403`(FORBIDDEN_ROLE) / 없는 정산·주최자가 본인 것이 아닌 정산 상세 `404`(SETTLEMENT_NOT_FOUND) / 잘못된 필터 `400`(INVALID_FILTER) |
| 보안 | admin 경로는 ADMIN, host 경로는 HOST(본인 host_user_id 정산만) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

### 정산 확정 명령 — `POST /api/admin/settlements/{id}/{action}`

운영자가 정산을 확정·지급 기록·보류·해제·재계산·재승인한다

| 항목 | 정의 |
| --- | --- |
| Owner | Payment-Service |
| 관련 Story·시나리오 | 시나리오 11(정산) |
| Request | Path: id, action(confirm·reapprove·mark-paid·hold·release·recalculate) / Header: `Idempotency-Key`(필수, 100자 이내) / Body: paidAt, paymentReference, memo(mark-paid는 송금 확인번호와 과거 지급 시각 필수) |
| 정상 | `200` — 처리 후 정산 상세. confirm은 CALCULATED → CONFIRMED, mark-paid는 CONFIRMED → PAID(실제 송금은 하지 않고 수동 기록), hold·release는 HELD 전환·해제, recalculate는 재계산, reapprove는 지급 전 환불 조정 후 재승인. 돈이 움직이는 명령(confirm·mark-paid·reapprove)은 실행 직전에 PG·예매·결제를 다시 대사하고, 모든 명령은 감사 로그(`settlement_audit_logs`)에 키·fingerprint와 함께 남는다 |
| 실패 | 인증 없음 `401`(Idempotency-Key 헤더 자체가 없을 때도 전역 핸들러가 401 `UNAUTHORIZED`) / host 경로·비운영자 `403`(FORBIDDEN_ROLE) / 없는 정산 `404` / 키가 비었거나 100자 초과 `400`(IDEMPOTENCY_KEY_REQUIRED) / 지원하지 않는 action `400`(UNKNOWN_ACTION) / mark-paid 확인번호 누락 `400`(PAYMENT_REFERENCE_REQUIRED) / 같은 키에 다른 내용 `409`(IDEMPOTENCY_KEY_CONFLICT) / 현재 상태에서 허용되지 않는 명령 `409`(SETTLEMENT_STATE_CONFLICT) / 다른 운영자가 먼저 변경 `409`(SETTLEMENT_VERSION_CONFLICT·CONCURRENT_MODIFICATION) / 결제·환불 변경으로 재대사 필요 `409`(RECONCILIATION_REQUIRED·PAYMENT_CHANGED·REFUND_CHANGED·REAPPROVAL_REQUIRED) / 확정된 정산 재계산 `409`(RECALCULATION_BLOCKED) |
| 보안 | ADMIN 권한(admin 경로만) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

## 전체 엔드포인트 목록 (상세 표가 없는 API)

위 상세 표에 없는 외부 API 24개입니다. 컨트롤러 매핑 어노테이션 기준으로 셌으며, 외부 API는 상세 55개 + 목록 24개 = 79개입니다(auth 21, festival 35, reservation 15, payment 8). 이 밖에 컨트롤러가 아닌 경로로 STOMP 엔드포인트 `/ws`(구독 `/topic/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats`, 인증 없음)와 업로드 이미지 정적 경로 `/api/festivals/images/**`·`/api/booths/images/**`가 있습니다.

| METHOD | path | 서비스 | 인증·권한 | 한 줄 설명 | 상태 |
| --- | --- | --- | --- | --- | --- |
| POST | `/api/auth/reset-password` | Auth-Service | 공개 · 이메일 인증 토큰 필요 | 인증 토큰(인증 후 10분, 1회용)으로 비밀번호 재설정, 모든 Refresh Token 폐기 | IMPLEMENTED |
| GET | `/api/auth/kakao/callback` | Auth-Service | 공개 | 카카오 인가 코드로 로그인·자동 가입 후 302 리다이렉트(실패 시 로그인 화면에 오류 코드) | IMPLEMENTED |
| GET | `/api/auth/google/callback` | Auth-Service | 공개 | 구글 로그인 콜백, 같은 이메일의 비밀번호 계정이 있으면 연동 동의 화면으로 302 | IMPLEMENTED |
| POST | `/api/auth/oauth/confirm-link` | Auth-Service | 공개 · 5분짜리 연동 토큰 필요 | 기존 이메일 계정에 구글 소셜 연동을 동의하고 로그인 | IMPLEMENTED |
| GET | `/api/auth/helper-invitations/{token}` | Auth-Service | 공개 · 초대 토큰이 곧 인증 | 도우미 초대 링크 확인(이메일 마스킹, 링크는 로그에 마스킹) | IMPLEMENTED |
| POST | `/api/auth/helper-invitations/{token}/accept` | Auth-Service | 공개 · 초대 토큰이 곧 인증 | 도우미가 비밀번호를 정해 계정 활성화, 세션 버전 증가 후 토큰 발급 | IMPLEMENTED |
| GET | `/api/users/me` | Auth-Service | 로그인 · HELPER 허용 | 내 정보(role, status, 담당 페스티벌 등). 비밀번호 변경 전에 발급된 Access Token은 여기서 401(PASSWORD_CHANGED_RELOGIN_REQUIRED) | IMPLEMENTED |
| PATCH | `/api/users/me/nickname` | Auth-Service | 로그인 · HELPER는 Gateway 403 | 닉네임 변경(중복 409) | IMPLEMENTED |
| PATCH | `/api/users/me/profile-setup` | Auth-Service | 로그인 · HELPER는 Gateway 403 | 소셜 최초 로그인 후 이름·닉네임·약관 동의 입력 | IMPLEMENTED |
| PATCH | `/api/users/me/password` | Auth-Service | 로그인 · HELPER는 Gateway 403 · 소셜 전용 계정 403 | 비밀번호 변경, 모든 Refresh Token 폐기 | IMPLEMENTED |
| DELETE | `/api/users/me` | Auth-Service | 로그인 · HELPER는 Gateway 403 | 동의 문구 확인 후 탈퇴(행은 남기고 개인정보 익명화) | IMPLEMENTED |
| GET | `/api/admin/hosts` | Auth-Service | ADMIN | 주최자(HOST) 목록·검색, 페이지 10건 | IMPLEMENTED |
| GET | `/api/admin/users` | Auth-Service | ADMIN | 회원 검색(닉네임·이메일·role·status), 페이징 | IMPLEMENTED |
| PATCH | `/api/admin/users/{userId}/suspend` | Auth-Service | ADMIN | 회원 정지(사유 필수, 본인·ADMIN·HELPER 정지 불가, ACTIVE만), Refresh Token 전부 폐기 | IMPLEMENTED |
| PATCH | `/api/admin/users/{userId}/unsuspend` | Auth-Service | ADMIN | 정지 해제(SUSPENDED만) | IMPLEMENTED |
| GET | `/api/host-applications/me/history` | Festival-Service | 로그인(본인) | 내 주최 신청 이력 전체(최신순) | IMPLEMENTED |
| GET | `/api/admin/festivals/host-counts` | Festival-Service | ADMIN | 주최자 id별 등록 페스티벌 개수(최대 100명) | IMPLEMENTED |
| GET | `/api/admin/festivals/summary` | Festival-Service | ADMIN | 운영자 대시보드 요약 수치(진행·예정 행사, 심사·취소 대기, 환불 진행 중) | IMPLEMENTED |
| GET | `/api/admin/festivals/operations` | Festival-Service | ADMIN | 공개된 적 있는 행사의 운영 상태(ALL·SCHEDULED·ONGOING·CLOSED·CANCELLED)·판매 현황 | IMPLEMENTED |
| POST | `/api/host/festivals/{festivalId}/helpers/{helperUserId}/resend` | Festival-Service | HOST(본인 행사) | 대기 중인 도우미 초대 링크를 교체해 재발송(60초 쿨다운, 24시간 10회) | IMPLEMENTED |
| POST | `/api/host/festivals/{festivalId}/helpers/{helperUserId}/invitation` | Festival-Service | HOST(본인 행사) | 예전 방식으로 만든 도우미 계정을 이메일 초대 방식으로 전환 | IMPLEMENTED |
| DELETE | `/api/host/festivals/{festivalId}/helpers/{helperUserId}` | Festival-Service | HOST(본인 행사) | 도우미 초대·계정 해지, 기존 세션 폐기 | IMPLEMENTED |
| GET | `/api/reservations/{id}` | Reservation-Service | 로그인(본인, 타인은 403) | 내 예매 상세 | IMPLEMENTED |
| GET | `/api/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats` | Reservation-Service | 공개 | SEATED 좌석 맵(좌석별 AVAILABLE·HELD·SOLD), 이후 변화는 STOMP로 수신 | IMPLEMENTED |

## 서비스 간 동기 계약

서비스 간 호출은 전부 동기 HTTP(Spring RestClient, Gateway만 WebClient)이며 Gateway를 거치지 않고 컨테이너 네트워크에서 직접 호출합니다. 내부 API 25개 중 20개는 아래 상세 표, 나머지 5개는 절 끝의 목록 표에 있습니다(auth 9, festival 6, reservation 9, payment 1). 모든 내부 API는 `Authorization` 헤더의 토큰이 다르면 `401`(INVALID_INTERNAL_TOKEN), 헤더가 아예 없으면 `401`(UNAUTHORIZED)로 거절합니다.

### grantHostRole — `PUT /internal/v1/roles`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Festival-Service → Auth-Service (호출 위치: 운영자 승인 `PATCH /api/admin/host-applications/{id}`와 HostApplicationApprovalRetryScheduler) |
| 관련 Story·시나리오 | Story 4 / 시나리오 4 |
| Request·Response | Body: userId, applicationId(멱등키), role=HOST / Response: ApiResponse 봉투(data 없음) |
| 내부 인증 | 허용 호출: festival-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(festival-service `internal.auth-service.token`, auth-service `internal.role-grant.token`이 같은 값을 읽음) |
| Timeout·재시도 | connect 1초 / read 5초(처음 2초였으나 운영 서버에서 응답이 2초를 넘겨 신청이 멈춘 사례 뒤 상향). 호출 자체 재시도는 없고, HostApplicationApprovalRetryScheduler가 60초마다 APPROVAL_PENDING에 30초 이상 머문 신청을 같은 applicationId로 다시 호출한다(운영자가 같은 승인을 다시 보내도 같은 호출) |
| 실패 시 사용자 결과·저장 여부 | 응답 유실·Timeout·오류 응답이면 신청은 APPROVAL_PENDING으로 저장된 채 남고 운영자에게 `202`. auth-service는 role_grants의 application_id unique로 같은 신청의 재호출을 다시 부여하지 않는다(멱등). 없는 사용자 `404`(USER_NOT_FOUND)·잘못된 role `400`(INVALID_ROLE)도 festival-service에서는 같은 실패로 처리되어 APPROVAL_PENDING으로 남는다. Role이 바뀌어도 이미 발급된 Access Token에는 재발급 전까지 반영되지 않는다 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (재시도 배치·read 5초는 Sprint 2) |

### checkAndDeductStock — `PATCH /internal/v1/ticket-types/{id}/stock`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Reservation-Service → Festival-Service |
| 관련 Story·시나리오 | Story 6 / 시나리오 6 |
| Request·Response | Path: id / Body: quantity(차감 수량) / Response: ApiResponse 봉투 |
| 내부 인증 | 허용 호출: reservation-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(festival-service `internal.auth-token`). 토큰이 없거나 다르면 `401`(INVALID_INTERNAL_TOKEN) — `TicketTypeInternalTokenTest`로 검증 |
| Timeout·재시도 | connect 1초 / read 2초, 재시도 없음 |
| 실패 시 사용자 결과·저장 여부 | 차감은 조건부 UPDATE(남은 수량이 요청 이상이고 페스티벌이 PUBLISHED일 때만)라 초과 차감이 없다. 조건 불충족이면 `409`(STOCK_EXCEEDED) → 사용자 `409`(STOCK_EXCEEDED), 그 밖의 실패·Timeout은 사용자 `503`(FESTIVAL_SERVICE_UNAVAILABLE). 두 경우 모두 예매는 저장되지 않는다. 차감 뒤 예매 저장이 실패하면 예약 서비스가 restoreStock으로 되돌린다 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 |

### restoreStock — `PATCH /internal/v1/ticket-types/{id}/stock/restore`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Reservation-Service → Festival-Service (Payment-Service는 이 API를 직접 호출하지 않고, 예약 서비스의 취소·환불 처리를 통해서만 간접적으로 재고가 복구됨). 호출 시점: 본인 PENDING 취소, 결제 실패 취소, 만료 배치, 예매 저장 실패 보상, 환불 재고·좌석 19시 반환 배치 |
| 관련 Story·시나리오 | 시나리오7(결제 실패·취소 시 재고 즉시 복구), 시나리오 9(환불 재고 반환) |
| Request·Response | Path: id / Body: `quantity`(양수) / Response: ApiResponse 봉투 |
| 내부 인증 | 허용 호출: reservation-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(checkAndDeductStock과 같은 검증) |
| Timeout·재시도 | connect 1초 / read 2초. 만료 배치에서 실패하면 `stock_release_queue`에 반환 시각을 지금으로 넣어 StockReleaseScheduler(60초)가 재시도, 19시 반환 배치에서 실패하면 반환 완료 표시를 비워 두고 다음 회차에 재시도 |
| 실패 시 사용자 결과·저장 여부 | 복구 수량이 총수량을 넘거나 티켓종류가 없으면 오류 없이 무시하고 경고 로그만 남긴다(원자적 조건 UPDATE). 사용자 본인 취소와 결제 실패 취소 경로에서는 복구 호출 예외가 그대로 전파되어 취소 트랜잭션이 롤백된다(전역 예외 처리가 없어 Spring 기본 500) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 1 (2026-09-02 추가), 만료 재고 재시도 대기열은 Week 4 안정화 (2026-09-24) |

### getReservationForPayment / confirmReservation / cancelReservation / extendReservationHold — `GET /internal/v1/reservations/{id}`, `PATCH /internal/v1/reservations/{id}/confirm`, `PATCH /internal/v1/reservations/{id}/cancel`, `PATCH /internal/v1/reservations/{id}/extend-hold`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Payment-Service → Reservation-Service |
| 관련 Story·시나리오 | Story 7(결제) |
| Request·Response | Path: id / confirm Body: paymentId, amount, payMethod, paidAt / cancel Body: paymentId, reasonCode(PAYMENT_FAILED 등) / extend-hold Body: expiresAt / Response: 봉투 없음(조회는 예매 DTO, 나머지는 빈 본문) |
| 내부 인증 | 허용 호출: payment-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(reservation-service `internal.auth-token`). Gateway를 거치지 않는 내부 전용 경로 |
| Timeout·재시도 | connect 1초 / read 2초. 호출 자체 재시도는 없고, 확정은 완료 API 재호출과 웹훅 재처리(WebhookRetryScheduler 60초, 최대 30회)가 다시 부른다 |
| 실패 시 사용자 결과·저장 여부 | confirm은 예매 행을 잠그고 판단한다. 같은 paymentId로 다시 오면 성공(멱등), 다른 결제로 이미 확정 `409`(RESERVATION_ALREADY_CONFIRMED), 만료 `409`(RESERVATION_ALREADY_EXPIRED), 다른 사유로 취소 `409`(RESERVATION_NOT_CANCELLABLE), 금액 불일치 `409`(PAYMENT_AMOUNT_MISMATCH). 409를 받은 결제 서비스는 결제에 확정 거절을 기록하고 자동 전액 환불(보상)한 뒤 사용자에게 `409`(RESERVATION_ALREADY_FINALIZED)를 돌려준다. confirm이 Timeout이면 결제는 PAID로 저장된 채 예매 확정 시각이 비어 있어 재호출 때 다시 확정한다. 조회 `404`는 사용자 `404`(RESERVATION_NOT_FOUND), 그 밖의 조회 실패는 `503`(RESERVATION_SERVICE_UNAVAILABLE). cancel은 이미 CANCELLED면 재고를 다시 복구하지 않고 무시(멱등), PENDING이 아니면 `409`. extend-hold는 PENDING이 아니면 무시(멱등) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-07·09-08 추가) |

### createHelperAccount / listHelperAccounts — `POST /internal/v1/helper-accounts`, `GET /internal/v1/helper-accounts`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Festival-Service → Auth-Service |
| 관련 Story·시나리오 | 도우미 계정 발급 |
| Request·Response | 생성 Body: festivalId, festivalName, festivalStartAt, festivalEndAt(행사 스냅샷), email / 목록 Query: festivalId / Response: ApiResponse 봉투, 발급 아이디와 초대 상태만 담고 비밀번호·원문 토큰은 없음 |
| 내부 인증 | 허용 호출: festival-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN |
| Timeout·재시도 | connect 1초 / read 5초(festival-service의 auth 호출 공용 설정), 재시도 없음 |
| 실패 시 사용자 결과·저장 여부 | festival-service가 호출 전에 이미 본인 소유 페스티벌인지 확인하므로 이 내부 API 자체는 소유권을 재검증하지 않음. auth의 404·403은 `404`(HELPER_ACCOUNT_NOT_FOUND)로 통일해 존재를 숨기고, 초대 관련 오류 코드는 같은 이름으로 전달, 응답 실패·Timeout은 `503`(AUTH_SERVICE_UNAVAILABLE). 메일 발송이 실패해도 계정은 저장된 채 `502`(INVITATION_SEND_FAILED)로 알려 재발송할 수 있다 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-08 추가), 이메일 초대 방식 전환은 Sprint 3 (2026-09-14) |

### `GET /internal/v1/helper-accounts/session`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Gateway → Auth-Service (HELPER 토큰의 허용 경로 요청마다) |
| 관련 Story·시나리오 | 도우미 계정 발급·현장 입장 검증 |
| Request·Response | Query: userId, festivalId, version(토큰의 helperSessionVersion) / Response: ApiResponse 봉투, data는 유효 여부(true·false) |
| 내부 인증 | 허용 호출: gateway · 토큰 환경변수 INTERNAL_AUTH_TOKEN |
| Timeout·재시도 | WebClient 3초 Timeout, 재시도 없음 |
| 실패 시 사용자 결과·저장 여부 | 계정이 활성 상태가 아니거나 행사 종료·세션 버전 불일치면 false → Gateway가 `401`. 인증 서비스 오류·Timeout도 유효성을 보장할 수 없으므로 `401`(fail-closed). 저장하는 데이터 없음 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

### generateSeats — `POST /internal/v1/seats`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Festival-Service → Reservation-Service (구현: `InternalSeatController`) |
| 관련 Story·시나리오 | 시나리오 15 |
| Request·Response | Body: festivalId, ticketTypeId, zone, seatLayout(행별 seatCount·excludedSeats 배열) / Response: 빈 본문(봉투 없음) |
| 내부 인증 | 허용 호출: festival-service · 토큰 환경변수 INTERNAL_RESERVATION_TOKEN(내부 호출 중 이 경로만 별도 토큰). 불일치 `401`(INVALID_INTERNAL_TOKEN) |
| Timeout·재시도 | connect 1초 / read 2초, 호출 자체 재시도 없음(실패해도 FestivalPublishRetryScheduler가 60초마다 PUBLISH_PENDING에 30초 이상 머문 페스티벌을 다시 재시도) |
| 실패 시 사용자 결과·저장 여부 | 이미 생성된 티켓종류면 existsByTicketTypeId로 건너뜀(멱등). 실패하면 페스티벌은 PUBLISH_PENDING으로 저장된 채 방문자에게 비공개이고, 운영자 심사 응답은 `200`(상태 PUBLISH_PENDING). ⚠️ 배포 환경 필수 설정: festival-service의 RESERVATION_SERVICE_URL(컨테이너 DNS 이름)과 두 서비스의 INTERNAL_RESERVATION_TOKEN(같은 값) — URL이 없으면 `localhost`로 폴백돼 ConnectException으로 영구 실패한다(2026-09-18 장애, 해결됨) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 |

### 환불 견적·반영 — `GET /internal/v1/reservations/{id}/refund-quote`, `PATCH /internal/v1/reservations/{id}/refund`, `GET /internal/v1/reservations/{id}/organizer-refund-quote`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Payment-Service → Reservation-Service |
| 관련 Story·시나리오 | Story 9(취소·환불), 시나리오 9(행사 취소 환불) |
| Request·Response | refund-quote Query: quantity(선택) → 견적 DTO(refundable, rejectReason, refundAmount, feePercent 등) / refund Body: paymentId, quantity, cancellationId(PortOne 취소 ID), seatIds(선택) → 빈 본문 / organizer-refund-quote → 위약금 0·남은 전량 견적 / 모두 봉투 없음 |
| 내부 인증 | 허용 호출: payment-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN |
| Timeout·재시도 | connect 1초 / read 2초. refund는 PG 취소가 확정된 뒤에만 부르고, 반영이 끝나지 않은 취소 기록(예매 반영 시각 없음)은 이후 재조회 대사(같은 키의 환불 재요청, 웹훅 처리, 행사 취소 환불 배치)가 다시 부른다 |
| 실패 시 사용자 결과·저장 여부 | 견적 조회 실패 → 사용자 `503`(RESERVATION_SERVICE_UNAVAILABLE), 취소 기록은 만들지 않음. refund는 예매 행을 잠그고 PortOne 취소 ID별로 한 번만 반영(`refund_receipts`) — 같은 취소가 웹훅과 API로 두 번 와도 수량은 한 번만 줄고, paymentId·수량이 다르면 `409`(PAYMENT_AMOUNT_MISMATCH), 남은 수량 초과 `409`(REFUND_QUANTITY_EXCEEDED). 좌석 id가 없으면 예약 서비스가 반환할 좌석을 고른다. 환불 재고·좌석은 반환 대기열에 넣고 19시 이후 반환 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 2 (2026-09-09 refund-quote·refund), Sprint 3 (2026-09-14 organizer-refund-quote) |

### 행사 취소 환불 대상·완료 — `GET /internal/v1/festivals/refund-candidates`, `POST /internal/v1/festivals/{id}/complete-cancellation`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Payment-Service(FestivalRefundScheduler) → Festival-Service |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request·Response | refund-candidates → 운영자 승인이 끝난 CANCELLATION_PENDING 행사 배열(festivalId, hostUserId, 요청자, 사유) / complete-cancellation Path: id → 빈 본문 / 봉투 없음 |
| 내부 인증 | 허용 호출: payment-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(festival-service `internal.auth-token`) |
| Timeout·재시도 | connect 2초 / read 5초. FestivalRefundScheduler가 60초마다 다시 돈다 |
| 실패 시 사용자 결과·저장 여부 | 사용자 요청이 아닌 배치 호출이라 실패하면 다음 주기에 재시도. 결제별 환불 항목(`festival_refund_items`, 결제별 고정 멱등키)을 만들어 위약금 없이 전액 환불하고, 모든 항목 성공과 모든 예매의 CANCELLED·REFUNDED 반영이 끝나야 complete-cancellation을 부른다. 이미 CANCELLED면 최초 완료 시각을 유지(멱등), 승인 전이면 `409`(CANCELLATION_NOT_APPROVED) |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

### 정산 근거 조회 — `GET /internal/v1/festivals/settlement-candidates`, `GET /internal/v1/festivals/{id}/settlement-context`, `GET /internal/v1/reservations/settlement-context`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Payment-Service(SettlementScheduler, 정산 명령) → Festival-Service, Reservation-Service |
| 관련 Story·시나리오 | 시나리오 11(정산) |
| Request·Response | settlement-candidates Query: page(0 이상, 100건씩) → 종료 24시간이 지난 정산 대상 행사 배열 / festivals settlement-context Path: id → 행사 소유자·정산 가능 시각·상태 / reservations settlement-context Query: festivalId → 해당 행사 예매 배열 / 모두 봉투 없음 |
| 내부 인증 | 허용 호출: payment-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN |
| Timeout·재시도 | festival 호출 connect 2초 / read 5초, reservation 호출 connect 1초 / read 2초. 배치는 매일 02:00(Asia/Seoul), 행사별 실패는 다음 회차에 재시도 |
| 실패 시 사용자 결과·저장 여부 | PG·예매·결제 세 기록이 모두 맞을 때만 정산 라인을 만들고, 누락·불일치는 금액을 추정하지 않고 보류 사유와 함께 HELD로 저장. 행사 정보가 없으면 MISSING_FESTIVAL_CONTEXT, 행사 취소 환불이 끝나지 않았으면 ORGANIZER_REFUND_PENDING으로 보류. page 음수 `400`(INVALID_SETTLEMENT_PAGE), 없는 행사 `404` |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Sprint 3 (2026-09-14 추가) |

### 행사 취소 영향 미리보기 — `GET /internal/v1/payments/refund-preview`

| 항목 | 정의 |
| --- | --- |
| 호출 주체 → 대상 | Festival-Service → Payment-Service (운영자 취소 요청 목록 `GET /api/admin/festivals/cancellation-requests`의 PENDING 조회 때) |
| 관련 Story·시나리오 | 시나리오 9(행사 취소 환불) |
| Request·Response | Query: festivalIds → 페스티벌별 예상 환불 결제 수·금액 배열(봉투 없음) |
| 내부 인증 | 허용 호출: festival-service · 토큰 환경변수 INTERNAL_AUTH_TOKEN(festival-service `internal.payment-service.token`, payment-service `internal.auth-token`) |
| Timeout·재시도 | connect 1초 / read 2초, 재시도 없음 |
| 실패 시 사용자 결과·저장 여부 | 실패하면 금액 정보 없이 취소 요청 목록만 `200`으로 반환(운영자 화면 보조 정보). 저장하는 데이터 없음 |
| 상태 | IMPLEMENTED |
| 추가·변경 Sprint | Week 4 안정화 (2026-09-27 추가) |

### 서비스 간 내부 API 목록 (상세 표가 없는 API)

| METHOD | path | 서비스 | 인증·권한 | 한 줄 설명 | 상태 |
| --- | --- | --- | --- | --- | --- |
| GET | `/internal/v1/users` | Auth-Service | INTERNAL_AUTH_TOKEN · festival-service, payment-service | ids로 회원 요약(이름·이메일 등) 조회, 한 번에 최대 200명. 호출 측은 실패하면 빈 결과로 대체(festival connect 1초·read 5초, payment connect 2초·read 5초) | IMPLEMENTED |
| GET | `/internal/v1/users/search` | Auth-Service | INTERNAL_AUTH_TOKEN · festival-service | keyword·role로 회원 id 검색(최대 500개), 운영자 심사 목록의 주최자 검색용. 실패하면 주최자 매칭 없이 진행 | IMPLEMENTED |
| POST | `/internal/v1/helper-accounts/{helperUserId}/invitation` | Auth-Service | INTERNAL_AUTH_TOKEN · festival-service | 예전 방식 도우미 계정을 이메일 초대 방식으로 전환 | IMPLEMENTED |
| POST | `/internal/v1/helper-accounts/{helperUserId}/resend` | Auth-Service | INTERNAL_AUTH_TOKEN · festival-service | festivalId를 확인한 뒤 도우미 초대 링크 재발송 | IMPLEMENTED |
| DELETE | `/internal/v1/helper-accounts/{helperUserId}` | Auth-Service | INTERNAL_AUTH_TOKEN · festival-service | festivalId를 확인한 뒤 도우미 초대·계정 해지(세션 버전 증가, Refresh Token 폐기) | IMPLEMENTED |

## 비동기 Event 계약

해당 없음 - 서비스 간 통신은 전부 동기 HTTP이며 메시지 큐(Kafka·RabbitMQ)·Redis·도메인 이벤트가 없습니다. 비동기처럼 보이는 처리는 모두 같은 서비스 안의 DB 기반 재시도 스케줄러입니다: HostApplicationApprovalRetryScheduler, FestivalPublishRetryScheduler, ReservationExpiryScheduler, StockReleaseScheduler, SeatReleaseScheduler, PaymentCompensationScheduler, WebhookRetryScheduler, FestivalRefundScheduler, SettlementScheduler, VirtualAccountDemoDepositScheduler. 브라우저로 가는 좌석 상태 알림은 서비스 간 Event가 아니라 reservation-service의 STOMP 브로드캐스트(`/topic/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats`, 메시지 seatId·status)입니다.

## 2026-09-29 변경 (PR #357·#359·#361·#363·#365·#367)

PR 6개는 2026-09-29 기준 리뷰 대기(OPEN)이며, 병합되면 아래 내용이 적용된다. 새 공개 API는 없다.

| 대상 | 변경 | 근거 |
| --- | --- | --- |
| createBooth (`POST /api/store/booths`) | 페스티벌이 공개(PUBLISHED) 상태가 아니면 409 `FESTIVAL_NOT_OPEN_FOR_BOOTH`("공개 중인 페스티벌에만 부스를 개설할 수 있습니다."). 기존 조건(STOREHOST만, 페스티벌당 1개)은 그대로 | PR #363, `BoothErrorCode`, `BoothService.createBooth`, `BoothAcceptanceTest` |
| 공통 헤더 `X-Trace-Id` | 게이트웨이는 요청에 값이 있으면 그대로, 없거나 비어 있으면 UUID를 만들어 하위 서비스로 보내고 응답 헤더에 한 번 붙인다. 각 서비스는 이 값을 로그(MDC `traceId`)에 싣고, 서비스 간 내부 호출에 이어 보낸다. 값이 `[A-Za-z0-9-]` 1~64자가 아니면 서비스가 새로 만든다. 서비스는 응답 헤더를 붙이지 않는다 | PR #359, `TraceIdGlobalFilter`(gateway), 각 서비스 `TraceIdFilter`·`TraceIdPropagationInterceptor` |
| 내부 `GET /internal/v1/helper-accounts/session` | 게이트웨이가 확인 중인 요청의 `X-Trace-Id`를 함께 보낸다. 계약(인증·응답)은 그대로 | PR #359, `HelperSessionClient` |
| 결제 상태 동기화 | API 변경 없음. `PaymentSyncScheduler`가 5분마다 생성 후 10분~24시간인 READY·PENDING·VIRTUAL_ACCOUNT_ISSUED 결제와 예매 확정 응답을 받지 못한 PAID 결제를 PortOne에 다시 조회해 기존 syncPayment 흐름으로 마무리한다 | PR #357 |
| 내부 `POST /internal/v1/seats` | 계약 변경 없음. 결번·멱등·토큰 401을 검증하는 테스트 추가 | PR #365, `InternalSeatGenerationAcceptanceTest` |