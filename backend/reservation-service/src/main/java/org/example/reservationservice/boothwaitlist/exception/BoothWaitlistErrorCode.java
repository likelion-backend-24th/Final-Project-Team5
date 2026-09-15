package org.example.reservationservice.boothwaitlist.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BoothWaitlistErrorCode implements ErrorCode {

    BOOTH_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않거나 아직 공개되지 않은 부스입니다."),
    BOOTH_NOT_OPEN(HttpStatus.CONFLICT, "대기 신청을 받고 있지 않은 부스입니다."),
    TICKET_NOT_FOUND(HttpStatus.FORBIDDEN, "이 페스티벌의 예매 내역이 있어야 대기 신청할 수 있습니다."),
    ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 대기 신청한 부스입니다."),
    WAITLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "대기 신청 내역이 없습니다."),
    FESTIVAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "부스 정보를 확인할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
