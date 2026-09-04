package org.example.festivalservice.domain.tickettype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TicketTypeErrorCode implements ErrorCode {

    STOCK_EXCEEDED(HttpStatus.CONFLICT, "재고가 부족합니다");

    private final HttpStatus httpStatus;
    private final String message;
}
