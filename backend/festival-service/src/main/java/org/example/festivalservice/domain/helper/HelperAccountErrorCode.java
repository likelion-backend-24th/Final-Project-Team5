package org.example.festivalservice.domain.helper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HelperAccountErrorCode implements ErrorCode {

    INVITATION_DUPLICATE(HttpStatus.CONFLICT, "이미 초대한 이메일입니다. 기존 초대를 재발송해주세요."),
    INVITATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "60초 뒤 재발송해주세요."),
    INVITATION_SEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "24시간 동안 최대 10회 발송할 수 있습니다."),
    INVITATION_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일 발송 실패. 계정은 보존되었으며 재발송할 수 있습니다."),
    INVITATION_ACCEPTED(HttpStatus.CONFLICT, "이미 활성화된 초대입니다."),
    INVITATION_REVOKED(HttpStatus.GONE, "해지된 초대입니다."),
    HELPER_FESTIVAL_ENDED(HttpStatus.GONE, "종료된 행사에는 초대할 수 없습니다."),
    FORBIDDEN_HOST_ROLE(HttpStatus.FORBIDDEN, "주최자만 도우미 계정을 관리할 수 있습니다."),
    AUTH_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "도우미 계정 정보를 확인할 수 없습니다."),
    HELPER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 도우미 계정입니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
