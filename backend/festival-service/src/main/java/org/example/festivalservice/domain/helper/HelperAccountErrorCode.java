package org.example.festivalservice.domain.helper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HelperAccountErrorCode implements ErrorCode {

    FORBIDDEN_HOST_ROLE(HttpStatus.FORBIDDEN, "주최자만 도우미 계정을 관리할 수 있습니다."),
    AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "도우미 계정 정보를 확인할 수 없습니다."),
    HELPER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 도우미 계정입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
