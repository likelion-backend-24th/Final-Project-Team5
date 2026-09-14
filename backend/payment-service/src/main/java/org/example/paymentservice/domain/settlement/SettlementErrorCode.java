package org.example.paymentservice.domain.settlement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SettlementErrorCode implements ErrorCode {
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없습니다."),
    INVALID_FILTER(HttpStatus.BAD_REQUEST, "조회 조건을 확인해 주세요."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "같은 키로 다른 요청이 이미 처리됐습니다."),
    UNKNOWN_ACTION(HttpStatus.BAD_REQUEST, "지원하지 않는 처리입니다."),
    SETTLEMENT_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서 허용되지 않는 처리입니다."),
    SETTLEMENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 관리자가 먼저 변경했습니다. 새로고침해 주세요."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "동시에 변경돼 처리하지 못했습니다. 다시 시도해 주세요."),
    RECALCULATION_BLOCKED(HttpStatus.CONFLICT, "확정된 정산은 다시 계산할 수 없습니다."),
    SETTLEMENT_NOT_ELIGIBLE(HttpStatus.CONFLICT, "정산 가능 시각 이전입니다."),
    RECONCILIATION_REQUIRED(HttpStatus.CONFLICT, "결제·환불 내역이 바뀌어 대사가 필요합니다. 다시 계산해 주세요."),
    PAYMENT_CHANGED(HttpStatus.CONFLICT, "결제 정보가 변경됐습니다. 다시 계산해 주세요."),
    REFUND_CHANGED(HttpStatus.CONFLICT, "환불 내역이 변경됐습니다. 다시 계산해 주세요."),
    REAPPROVAL_REQUIRED(HttpStatus.CONFLICT, "지급 전 환불 조정이 있어 재승인이 필요합니다."),
    REAPPROVAL_BLOCKED(HttpStatus.CONFLICT, "재승인할 수 없는 상태입니다."),
    NEGATIVE_PAYOUT_REVIEW_REQUIRED(HttpStatus.CONFLICT, "지급액이 음수라 검토가 필요합니다."),
    SETTLEMENT_NOT_FROZEN(HttpStatus.CONFLICT, "확정 전 정산은 대사 대상이 아닙니다."),
    PAYMENT_REFERENCE_REQUIRED(HttpStatus.BAD_REQUEST, "송금 확인 번호와 실제 지급 시각이 필요합니다."),
    MISSING_FESTIVAL_CONTEXT(HttpStatus.CONFLICT, "정산에 필요한 행사 정보를 확인할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
