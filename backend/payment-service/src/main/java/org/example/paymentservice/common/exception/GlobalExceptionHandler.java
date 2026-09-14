package org.example.paymentservice.common.exception;

import org.example.paymentservice.common.dto.ApiResponse;
import org.example.paymentservice.domain.settlement.SettlementErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException e) {
        //정산 확정의 낙관적 락 충돌과 조정 행의 unique 충돌은 모두 "다시 시도하면 되는" 경합이라 같은 코드로 응답한다.
        var code = SettlementErrorCode.CONCURRENT_MODIFICATION;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code.name(), code.getMessage()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(ApiException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.error(errorCode.name(), e.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingAuthHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("UNAUTHORIZED", "인증 정보가 없습니다"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("잘못된 요청입니다");
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_REQUEST", message));
    }
}
