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
    FORBIDDEN_HELPER_FESTIVAL(HttpStatus.FORBIDDEN, "해당 페스티벌의 도우미 계정이 아닙니다."),
    HELPER_ACCOUNT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "도우미 계정 생성에 실패했습니다."),
    HELPER_PENDING_ACTIVATION(HttpStatus.FORBIDDEN, "이메일 초대 링크에서 계정을 활성화해주세요."),
    HELPER_FESTIVAL_ENDED(HttpStatus.GONE, "종료된 행사입니다."),
    INVITATION_DUPLICATE(HttpStatus.CONFLICT, "이미 초대한 이메일입니다. 기존 초대의 재발송을 이용해주세요."),
    INVITATION_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "비밀번호는 8자 이상, UTF-8 기준 72바이트 이하여야 합니다."),
    INVITATION_INVALID(HttpStatus.NOT_FOUND, "유효하지 않은 초대 링크입니다."),
    INVITATION_EXPIRED(HttpStatus.GONE, "초대 링크가 만료되었습니다. 주최자에게 재발송을 요청해주세요."),
    INVITATION_ACCEPTED(HttpStatus.CONFLICT, "이미 사용한 초대 링크입니다."),
    INVITATION_REVOKED(HttpStatus.GONE, "해지된 초대 또는 계정입니다."),
    INVITATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "초대 발송 후 60초 뒤 재발송할 수 있습니다."),
    INVITATION_SEND_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "24시간 동안 최대 10회 발송할 수 있습니다."),
    INVITATION_SEND_FAILED(HttpStatus.BAD_GATEWAY, "메일 발송에 실패했습니다. 계정은 보존되었으며 재발송할 수 있습니다."),
    HELPER_SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "만료된 도우미 세션입니다. 다시 로그인해주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
