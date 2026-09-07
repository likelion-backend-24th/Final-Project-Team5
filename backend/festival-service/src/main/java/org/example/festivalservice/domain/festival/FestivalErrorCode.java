package org.example.festivalservice.domain.festival;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorCode implements ErrorCode {

    FORBIDDEN_ADMIN_ROLE(HttpStatus.FORBIDDEN, "운영자 권한이 없습니다."),
    FORBIDDEN_HOST_ROLE(HttpStatus.FORBIDDEN, "주최자 권한이 없습니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 페스티벌입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인 소유 페스티벌만 조회할 수 있습니다."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "공개 또는 반려만 결정할 수 있습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 심사 처리된 페스티벌입니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "종료 일시는 시작 일시 이후여야 합니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "이미지는 최대 3개까지 등록할 수 있습니다."),
    INVALID_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "이미지 용량은 파일당 10MB를 초과할 수 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");


    private final HttpStatus httpStatus;
    private final String message;
}
