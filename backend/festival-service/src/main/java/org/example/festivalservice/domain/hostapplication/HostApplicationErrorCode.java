package org.example.festivalservice.domain.hostapplication;


import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HostApplicationErrorCode implements ErrorCode {

    ALREADY_HOST(HttpStatus.CONFLICT, "이미 주최자 권한을 가지고 있습니다."),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "이미 처리 대기 중인 신청이 있습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않은 신청입니다."),
    FORBIDDEN_ADMIN_ROLE(HttpStatus.FORBIDDEN, "운영자 권한이 없습니다."),
    FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "운영자는 주최자 신청을 할 수 없습니다."),
    APPROVAL_PENDING_CANNOT_REJECT(HttpStatus.CONFLICT, "Role 부여 처리 중인 신청은 반려할 수 없습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "승인 또는 반려만 결정할 수 있습니다."),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "반려 시 사유는 필수입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}