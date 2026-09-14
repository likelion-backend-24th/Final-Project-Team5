# 결제·환불·정산 운영 및 검증

## 사용자 흐름

- HOST: 내 페스티벌 → 내 정산. 본인 행사별 목록, 요약, 상세, 산식, 결제수단별 합계와 지급 후 조정/미수금을 읽기 전용으로 조회한다.
- ADMIN: 관리자 → 정산 대시보드. 계산일/결제일, 기간, 상태, 행사, 주최자, 결제수단으로 조회하고 상세에서 재계산, 확정, 수동 보류/해제, 지급 완료를 처리한다.
- 지급 완료는 외부 송금 이후 실제 지급 시각과 지급 식별자를 입력하는 기록이다. 은행 이체 API는 호출하지 않는다.
- HOST는 행사 상세에서 취소 사유를 입력해 승인을 요청한다. 즉시 `CANCELLATION_PENDING`으로 전환하여 신규 예매를 막는다. ADMIN은 정산 대시보드의 행사 취소 요청에서 전액 환불을 승인한다.
- 승인된 행사 환불은 60초 간격 작업자가 개별 결제 작업을 처리한다. 실패 항목은 같은 멱등 키로 재시도한다. 모든 결제 작업 성공과 Reservation 환불 반영을 확인한 다음에만 행사 `CANCELLED`로 전환한다. 입장 완료 티켓도 남은 수량에 대해 위약금 0%를 적용한다.

## 금액 규칙

결제별 `수수료 = floor((결제액 - 성공 취소 액면가 합계) × bps / 10000)`.

- CARD/EASY_PAY: 750 bps. VIRTUAL_ACCOUNT: 500 bps. VAT 포함 최종 공제율.
- 구매자 결제액은 ticketAmount이며 platformFee를 더하지 않는다.
- 취소 위약금은 액면가에서 실제 환급액을 뺀 금액이고 주최자에게 귀속된다.
- 지급액 = 결제액 - 실제 환급액 - 최종 플랫폼 수수료 + 이전 정산 조정액.
- 수수료 환입 = 최초 수수료 - 환불 후 남은 매출의 수수료. 여러 부분 환불에서는 직전 잔액 수수료와 새 잔액 수수료의 차이를 기록하여 누적 오차를 없앤다. 예: 13원 티켓 두 장의 카드 최초 수수료는 1원이며 한 장 환불 후 수수료는 0원, 환입은 1원이다.
- 이전 정산 조정이 없는 원장에서는 `결제액 - 실제 환급액 = 주최자 지급액 + 플랫폼 수수료`가 성립한다. 이월 조정을 포함한 원장의 검증에서는 이월 미수금/상계 원장도 함께 대사한다.
- PG 비용은 이 산식에서 주최자에게 전가하지 않는다.

## HTTP 계약

`/api/admin/settlements`와 `/api/host/settlements`에 summary, 목록, `/{id}` 상세를 제공한다. HOST는 Gateway의 X-User-Id로 소유권을 검증하며 요청 hostUserId는 소유권 조건으로 사용하지 않는다. 타인 상세는 404, 역할 불일치는 403이다.

ADMIN 명령: `POST /api/admin/settlements/{id}/{recalculate|confirm|reapprove|hold|release|mark-paid}`. Idempotency-Key 필수. 본문은 `{paidAt, paymentReference, memo}`이며 지급 시각은 ISO Instant이다. confirm/reapprove/mark-paid는 같은 키와 같은 요청에 멱등적이고 키 재사용 시 요청이 다르면 409이다.

목록은 0 기반 page, size(1~100), from(포함), to(미포함), status, festivalId, hostUserId, paymentMethod, testPayment, dateBasis(PAID_AT 또는 SETTLEMENT_AT)를 지원한다. 최신 정산 ID 순이다. `meta.pagination`은 page, size, totalItems, totalPages, hasNext, hasPrev를 제공한다. 금액은 KRW 정수다. 결제일 필터는 해당 기간 결제가 있는 페스티벌을 선택하며 금액은 선택된 페스티벌 전체 정산 스냅샷이다.

HOST DTO에는 내부 메모, 원시 보류 사유, 지급 식별자, 감사 이력, 결제/예매 식별자를 포함하지 않는다. 공개 가능한 보류 안내와 조정 금액만 제공한다.

내부 API는 기존 Authorization Bearer INTERNAL_AUTH_TOKEN 계약을 따른다.

- Festival: GET `/internal/v1/festivals/settlement-candidates?page=0` (100건 단위), GET `/{id}/settlement-context`, GET `/refund-candidates`, POST `/{id}/complete-cancellation`.
- Reservation: GET `/internal/v1/reservations/settlement-context?festivalId=...`, GET `/{id}/organizer-refund-quote`.
- 기존 Reservation 결제 준비 응답에 festivalId, hostUserId, unitPrice, refundedQuantity, paymentId를 추가한다. 양쪽 DTO와 기존 응답 필드는 유지한다.
- 기존 환불 적용 요청에 cancellationId를 추가한다. 신규 호출은 취소 ID를 반드시 보내며 수신 서비스는 같은 ID의 수량 적용과 재고 반환 예약을 한 번만 수행한다.
- HOST POST `/api/host/festivals/{id}/cancellation-request`, ADMIN POST `/api/admin/festivals/{id}/approve-cancellation`, GET `/api/admin/festivals/cancellation-requests`.

## 데이터 및 동시성

Payment에 행사/주최자/단가/승인 시각, 원본 수단, 정규화 수단, 간편결제사, bps, 정책 버전, 테스트 여부, 예매 확정 반영 시각을 추가한다. Transaction에도 원본/정규화 수단과 간편결제사를 저장한다. 기존 가상계좌 발급 거래가 승인되면 승인 스냅샷을 갱신한다.

Cancellation에 액면가, 위약금률/금액, 수수료 환입, 업무 사유, 요청자, 예매 반영 시각을 추가한다. 실제 환급액은 기존 amount 컬럼을 사용한다. activePaymentId의 유니크 제약으로 같은 결제의 진행 중 환불 중복 접수를 차단한다.

신규 테이블: settlements, settlement_lines, settlement_adjustments, settlement_adjustment_allocations, settlement_audit_logs, festival_refund_batches, festival_refund_items, refund_receipts(Reservation 소유).

Settlement는 festivalId+testPayment, Line은 settlementId+paymentId, Batch는 festivalId, Item은 batchId+paymentId를 유니크 키로 사용한다. 정산 명령 키와 조정의 원본 결제/누적 환불도 유니크 제약으로 보호한다. Settlement 및 Payment/Festival의 낙관적 락과 환불 수신 Reservation의 비관적 락을 사용한다. 기존 Payment/Festival의 version 컬럼은 DEFAULT 0으로 추가한다.

스케줄은 매일 02:00 Asia/Seoul이며 종료 시각은 Festival의 KST 시각을 Instant로 변환하고 24시간을 더한다. DB 스키마 간 직접 조인은 없다. 외부 조회는 정산 저장 트랜잭션 전에 완료하며 외부 장애 시 확정하지 않는다. 확정/지급 직전에 재대사한다.

일반 재계산은 PENDING/CALCULATED/HELD에서만 가능하다. 수동 보류는 release 전까지 자동 해제하지 않는다. 확정/지급 후 스냅샷 금액은 변경하지 않는다. 지급 후 차액은 Adjustment에 남고 다음 미확정 정산에 상계한다. 다음 정산 지급액이 부족하면 지급 가능액까지만 상계하고 남은 금액은 RECEIVABLE로 유지한다. 할당은 별도 Allocation 원장에 기록하며 재계산/보류 시 미확정 할당을 먼저 되돌려 이중 상계를 방지한다.

확정 후 지급 전 환불은 PRE_PAYMENT 조정을 만들고 ADJUSTMENT_REQUIRED로 바꾼다. ADMIN의 reapprove는 재대사 후 최초 스냅샷을 유지하며 별도 confirmedAdjustmentAmount와 reapprovedAt을 기록하고 CONFIRMED로 전환한다. mark-paid는 현재 승인된 지급액을 paidPayoutAmount에 기록한다. 지급이 끝난 정산에는 reapprove를 허용하지 않는다. 최초 산출 지급액과 조정 후 지급액을 화면에서 구분한다.

이전 미수금을 상계한 정산이 지급 전에 추가 환불되어 지급액이 음수가 될 경우, 재승인 시 부족액만큼 기존 상계를 취소하고 원래 미수금으로 돌린다. 이 상계 해제도 별도 양수 PRE_PAYMENT 조정으로 기록하여 최초 확정 금액은 바꾸지 않는다.

## 보류 및 기존 데이터

UNKNOWN 수단, 진행 중 취소, 외부 취소 수량 0, 환불 스냅샷 누락, PG 누적 취소액과 성공 Cancellation 합계 불일치, Reservation 수량/금액 불일치, 귀책 환불 진행 중에는 확정할 수 없다.

Flyway는 도입하지 않는다. 기존 ddl-auto=update를 유지하고 스냅샷 신규 컬럼은 nullable이다. 미정산 기존 결제는 Reservation 내부 조회와 PortOne 단건 재조회로 확인한 현재 승인 정보를 사용해 새 정책의 SettlementLine을 만든다. 과거 Cancellation의 누락된 액면가를 위약금률 등으로 추정하지 않는다.

운영 보정은 이 변경에서 실행하지 않았다. 별도 승인을 받은 운영 절차:

1. 백업 및 복원 검증 후 대상 Payment/Transaction/Cancellation/Reservation 목록을 읽기 전용으로 추출한다.
2. PortOne storeId, paymentId, 승인 시각, 통화, 실제 수단/간편결제사, 채널 TEST/LIVE, 누적 취소액을 재조회한다.
3. Reservation 내부 API의 원수량/환불수량/단가와 Festival 소유자를 대조한다. 취소 ID별 액면가/환급액/수량은 실제 근거가 있을 때만 보정 후보로 제시한다.
4. 보정 전후 건수·금액·누적 수량·수수료를 비교한 승인 가능한 변경 목록을 만든다. 운영 UPDATE/DELETE는 별도 명시 승인 없이 실행하지 않는다.
5. 보정 후 미확정 정산만 재계산한다. 이미 지급한 원장은 수정하지 않고 조정 원장을 확인한다.

개발에서 테스트 거래를 조회하려면 Payment Service `settlement.allow-test-payments=true`를 설정한다. 스케줄러가 테스트/실거래 원장을 분리 생성하고 개발 프론트의 테스트 거래 필터로 조회한다. 기본값은 false이며 API에서도 테스트 조회를 차단한다. 기존 결제 승인 검증의 TEST 채널 제한은 이번 변경에서 운영 LIVE 승인으로 전환하지 않았다.

## 공식 계약과 기존 구현의 차이

[PortOne V2 공식 결제 API](https://developers.portone.io/api/rest-v2/payment)의 Method discriminator는 CARD/EASY_PAY/VIRTUAL_ACCOUNT 문자열이다. PaymentMethodCard 등은 타입 이름으로, 기존 일부 테스트 픽스처를 실제 discriminator로 수정했다. EasyPay의 provider와 easyPayMethod를 수신하되 카드 상세정보는 저장하지 않는다.

기존 구현의 취소 성공 확인 전 예매 환불 적용, 부분 환불 콜백의 중복 수량 반영, 멱등 키를 통한 타인 취소 참조, 승인 후 예매 확정 응답 유실 복구 누락도 수정했다. 취소 응답 유실 시 금액만 같다는 이유로 외부 취소를 로컬 요청에 임의 연결하지 않는다. 이 차이는 원장 수량 오인 연결을 막기 위한 보류 우선 처리다.

## 검증 및 남은 운영 확인

2026-09-14 자동 검증: payment-service 83개, reservation-service 37개, festival-service 51개, gateway 13개 테스트 통과(총 184개, 실패/건너뜀 0). 프론트 React 상태/권한/확인 UI 테스트 6개 통과. 각 서비스 bootJar와 프론트 production build 성공. 프론트 번들 크기 경고는 남아 있다.

주요 추가 테스트: SettlementCalculatorTest, SettlementAcceptanceTest, SettlementContractTest, FestivalRefundSchedulerTest, OrganizerRefundAcceptanceTest, FestivalCancellationAcceptanceTest, SettlementReport.test.jsx.

실행 명령:

```powershell
cd backend
.\gradlew.bat :payment-service:test :reservation-service:test :festival-service:test :gateway:test :payment-service:bootJar :reservation-service:bootJar :festival-service:bootJar :gateway:bootJar
cd ../frontend
npm test
npm run build
npm run lint
```

Docker 이미지 생성 전에 각 bootJar를 생성해야 한다. 운영 DB 보정, SSH 쓰기, 배포, 실제 PG 결제/환불/은행 송금은 실행하지 않는다.

수동 확인: PortOne 테스트 팝업(카드/카카오페이/가상계좌), 비동기 취소 웹훅과 지연/실패 재시도, 기존 DB 복제본의 ddl-auto 스키마 추가, 모바일 화면/키보드 조작, 운영 외부 송금 절차.

후속 운영 경로가 필요한 항목:

- PG 취소 ID 응답 유실 후 외부 발견 행과 원래 요청 행이 각각 남는 경우 자동으로 수량을 연결하지 않는다. 대사 근거 확인과 승인된 보정이 필요하다. PG가 명시적으로 FAILED를 반환한 취소의 새 PG 요청 키 발급도 운영 확인 대상으로 남긴다.
- 실제 LIVE 결제 승인 활성화는 기존 테스트 채널 제한 정책을 변경하므로 별도 운영 검토가 필요하다.
- 미수금의 현금 회수/회수 완료 기록과 실제 송금은 포함하지 않는다. 다음 정산 상계와 미수금 조회까지만 제공한다.
