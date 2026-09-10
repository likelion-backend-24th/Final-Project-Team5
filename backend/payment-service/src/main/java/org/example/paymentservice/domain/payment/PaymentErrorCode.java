package org.example.paymentservice.domain.payment;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예매를 찾을 수 없습니다"),
    FORBIDDEN_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "본인 예매만 결제할 수 있습니다"),
    RESERVATION_NOT_PAYABLE(HttpStatus.CONFLICT, "결제할 수 없는 예매 상태입니다"),
    RESERVATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "예매 정보를 확인할 수 없습니다"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 건을 찾을 수 없습니다"),
    FORBIDDEN_PAYMENT_OWNER(HttpStatus.FORBIDDEN, "본인 결제만 확인할 수 있습니다"),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.CONFLICT, "결제 정보가 일치하지 않습니다"),
    UNEXPECTED_PAYMENT_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 결제 상태입니다"),
    PAYMENT_NOT_YET_PROCESSED(HttpStatus.CONFLICT, "결제가 아직 진행되지 않았습니다"),
    RESERVATION_ALREADY_FINALIZED(HttpStatus.CONFLICT, "예매가 이미 만료되었거나 취소되었습니다"),
    //환불(Story 9). 거절 사유는 Reservation-Service가 판정한 것을 그대로 옮겨, 화면이 이유를 구분해 안내할 수 있게 한다.
    PAYMENT_NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다"),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT, "환불할 수 없는 예매입니다"),
    REFUND_WINDOW_CLOSED(HttpStatus.CONFLICT, "공연 시작이 임박해 환불할 수 없습니다"),
    ALREADY_CHECKED_IN_NOT_REFUNDABLE(HttpStatus.CONFLICT, "이미 입장한 예매는 환불할 수 없습니다"),
    REFUND_QUANTITY_EXCEEDED(HttpStatus.CONFLICT, "환불 가능한 수량을 초과했습니다"),
    REFUND_FAILED(HttpStatus.BAD_GATEWAY, "결제사 취소 요청에 실패했습니다");

    private final HttpStatus httpStatus;
    private final String message;
}
