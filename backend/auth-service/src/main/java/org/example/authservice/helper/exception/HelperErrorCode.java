package org.example.authservice.helper.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HelperErrorCode implements ErrorCode {

    INVALID_INTERNAL_TOKEN(HttpStatus.UNAUTHORIZED, "내부 호출 인증에 실패했습니다."),
    HELPER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 도우미 계정입니다."),
    //다른 페스티벌의 도우미 계정을 대상으로 재발급을 시도한 경우 — 존재 여부를 흘리지 않도록 404와 같은 취급을 하지 않고 명시적으로 막는다.
    FORBIDDEN_HELPER_FESTIVAL(HttpStatus.FORBIDDEN, "해당 페스티벌의 도우미 계정이 아닙니다."),
    HELPER_ACCOUNT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "도우미 계정 생성에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
