package org.example.authservice.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    HttpStatus getHttpStatus();
    String getMessage();
    String name();
}
