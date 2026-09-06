package org.example.authservice.role.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RoleErrorCode implements ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."),
    INVALID_INTERNAL_TOKEN(HttpStatus.UNAUTHORIZED, "내부 서비스 인증 토큰이 유효하지 않습니다."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 role 값입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
