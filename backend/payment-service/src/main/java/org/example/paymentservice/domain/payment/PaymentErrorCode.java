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
    RESERVATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "예매 정보를 확인할 수 없습니다");

    private final HttpStatus httpStatus;
    private final String message;
}
