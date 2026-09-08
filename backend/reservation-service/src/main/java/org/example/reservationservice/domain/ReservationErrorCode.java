package org.example.reservationservice.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {

    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "예매 수량은 1장 이상이어야 합니다."),
    FESTIVAL_NOT_PUBLISHED(HttpStatus.NOT_FOUND, "존재하지 않거나 예매할 수 없는 페스티벌입니다."),
    TICKET_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 티켓 종류입니다."),
    STOCK_EXCEEDED(HttpStatus.CONFLICT, "재고가 부족합니다."),
    FESTIVAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "페스티벌 정보를 확인할 수 없습니다."),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 예매입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인 예매만 조회할 수 있습니다."),
    RESERVATION_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 결제 확정된 예매입니다."),
    RESERVATION_ALREADY_EXPIRED(HttpStatus.CONFLICT, "이미 만료 처리된 예매입니다."),
    RESERVATION_NOT_CANCELLABLE(HttpStatus.CONFLICT, "취소할 수 없는 예매 상태입니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "결제 금액이 예매 금액과 일치하지 않습니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "1인당 구매 가능 수량을 초과했습니다."),
    INVALID_INTERNAL_TOKEN(HttpStatus.UNAUTHORIZED, "내부 호출 인증에 실패했습니다."),
    RESERVATION_NOT_CONFIRMED(HttpStatus.CONFLICT, "결제가 확정된 예매만 QR을 발급받을 수 있습니다."),
    INVALID_QR_TOKEN(HttpStatus.NOT_FOUND, "유효하지 않은 QR입니다."),
    INVALID_CHECK_IN_CODE(HttpStatus.NOT_FOUND, "유효하지 않은 입장 코드입니다."),
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "이미 입장 처리된 티켓입니다."),
    OTHER_FESTIVAL_TICKET(HttpStatus.CONFLICT, "다른 공연의 티켓입니다."),
    FESTIVAL_NOT_STARTED(HttpStatus.CONFLICT, "아직 공연 시작 전이라 입장할 수 없습니다."),
    FORBIDDEN_NOT_ORGANIZER(HttpStatus.FORBIDDEN, "본인이 주최한 페스티벌의 예매만 검증할 수 있습니다."),
    //도우미 계정인데 담당 페스티벌 정보(X-Festival-Id)가 없는 비정상 토큰으로 들어온 경우
    HELPER_FESTIVAL_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "담당 페스티벌이 지정되지 않은 도우미 계정입니다."),
    CHECK_IN_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "입장 코드 발급에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
