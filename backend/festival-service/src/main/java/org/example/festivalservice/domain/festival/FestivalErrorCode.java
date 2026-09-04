package org.example.festivalservice.domain.festival;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorCode implements ErrorCode {

    FORBIDDEN_ADMIN_ROLE(HttpStatus.FORBIDDEN, "운영자 권한이 없습니다."),
    FORBIDDEN_HOST_ROLE(HttpStatus.FORBIDDEN, "주최자 권한이 없습니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 페스티벌입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인 소유 페스티벌만 조회할 수 있습니다."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "공개 또는 반려만 결정할 수 있습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 심사 처리된 페스티벌입니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "종료 일시는 시작 일시 이후여야 합니다.");


    private final HttpStatus httpStatus;
    private final String message;
}
