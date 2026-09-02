package org.example.authservice.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
