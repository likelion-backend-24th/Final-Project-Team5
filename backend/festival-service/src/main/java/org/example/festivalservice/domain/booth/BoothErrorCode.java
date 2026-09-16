package org.example.festivalservice.domain.booth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum BoothErrorCode implements ErrorCode {

    FORBIDDEN_STOREHOST_ROLE(HttpStatus.FORBIDDEN, "부스 운영자 권한이 없습니다."),
    DUPLICATE_BOOTH_FOR_FESTIVAL(HttpStatus.CONFLICT, "이미 이 페스티벌에 개설된 부스가 있습니다."),
    BOOTH_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 부스입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인이 개설한 부스만 관리할 수 있습니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 페스티벌입니다."),
    INVALID_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "이미지 용량은 10MB를 초과할 수 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
