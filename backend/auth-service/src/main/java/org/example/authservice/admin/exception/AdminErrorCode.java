package org.example.authservice.admin.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdminErrorCode implements ErrorCode {

    CANNOT_SUSPEND_SELF(HttpStatus.BAD_REQUEST, "본인 계정은 정지할 수 없습니다."),
    CANNOT_SUSPEND_ADMIN(HttpStatus.BAD_REQUEST, "운영자 계정은 정지할 수 없습니다."),
    CANNOT_SUSPEND_MANAGED_ACCOUNT(HttpStatus.BAD_REQUEST, "도우미·부스 계정은 주최자가 관리하므로 정지할 수 없습니다."),
    USER_NOT_ACTIVE(HttpStatus.CONFLICT, "정상 상태인 회원만 정지할 수 있습니다."),
    USER_NOT_SUSPENDED(HttpStatus.CONFLICT, "정지 상태인 회원만 정지 해제할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}