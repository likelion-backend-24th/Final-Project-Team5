package org.example.festivalservice.common.exception;

import lombok.Getter;

/** 실패 응답으로 변환할 상태코드·에러코드·메시지를 담는 비즈니스 예외. */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
