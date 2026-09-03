package org.example.authservice.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;


@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 계정입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "비밀번호가 일치하지 않습니다."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 계정입니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.FORBIDDEN, "회원탈퇴한 사용자입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh token입니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "재사용이 감지되어 모든 세션이 로그아웃되었습니다."),
    INVALID_INTERNAL_TOKEN(HttpStatus.UNAUTHORIZED, "내부 서비스 인증 토큰이 유효하지 않습니다."),
    INVALID_ROLE(HttpStatus.BAD_REQUEST, "유효하지 않은 role 값입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
