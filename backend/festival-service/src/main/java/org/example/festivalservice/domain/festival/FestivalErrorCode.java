package org.example.festivalservice.domain.festival;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FestivalErrorCode implements ErrorCode {
    FESTIVAL_NOT_CANCELLABLE(HttpStatus.CONFLICT, "공개 또는 종료 상태의 페스티벌만 취소할 수 있습니다."),
    CANCELLATION_NOT_REQUESTED(HttpStatus.CONFLICT, "취소 요청이 없는 페스티벌입니다."),
    CANCELLATION_NOT_APPROVED(HttpStatus.CONFLICT, "운영자 승인 전에는 취소를 완료할 수 없습니다."),
    CANCELLATION_ALREADY_APPROVED(HttpStatus.CONFLICT, "이미 승인돼 환불이 진행 중인 취소 요청은 반려할 수 없습니다."),
    CANCEL_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "취소 사유는 필수입니다."),
    INVALID_INTERNAL_TOKEN(HttpStatus.UNAUTHORIZED, "내부 호출 인증에 실패했습니다."),
    INVALID_SETTLEMENT_PAGE(HttpStatus.BAD_REQUEST, "정산 페이지는 0 이상이어야 합니다."),
    FORBIDDEN_ADMIN_ROLE(HttpStatus.FORBIDDEN, "운영자 권한이 없습니다."),
    FORBIDDEN_HOST_ROLE(HttpStatus.FORBIDDEN, "주최자 권한이 없습니다."),
    FESTIVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 페스티벌입니다."),
    FORBIDDEN_NOT_OWNER(HttpStatus.FORBIDDEN, "본인 소유 페스티벌만 조회할 수 있습니다."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "공개 또는 반려만 결정할 수 있습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "이미 심사 처리된 페스티벌입니다."),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "반려 시 사유는 필수입니다."),
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "종료 일시는 시작 일시 이후여야 합니다."),
    INVALID_OPERATING_HOURS(HttpStatus.BAD_REQUEST, "운영 종료 시간은 시작 시간 이후여야 합니다."),
    INVALID_TICKET_SALE_PERIOD(HttpStatus.BAD_REQUEST, "티켓 판매 종료 일시는 판매 시작 일시 이후여야 합니다."),
    INVALID_TICKET_DATE(HttpStatus.BAD_REQUEST, "티켓 날짜는 페스티벌 개최 기간 내여야 합니다."),
    INVALID_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "대표 이미지는 1개까지 등록할 수 있습니다."),
    INVALID_DETAIL_IMAGE_COUNT(HttpStatus.BAD_REQUEST, "본문 이미지는 최대 2개까지 등록할 수 있습니다."),
    INVALID_IMAGE_SIZE(HttpStatus.BAD_REQUEST, "이미지 용량은 파일당 10MB를 초과할 수 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "이미지 파일만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    INVALID_SEAT_LAYOUT(HttpStatus.BAD_REQUEST, "좌석 배치(zone/rows/seatsPerRow)가 올바르지 않습니다."),
    SEAT_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "좌석 생성 처리 중입니다. 잠시 후 다시 확인해주세요."),
    PUBLISH_PENDING_CANNOT_REJECT(HttpStatus.CONFLICT, "좌석 생성 처리 중인 페스티벌은 반려할 수 없습니다.");

    private final HttpStatus httpStatus;

    private final String message;
}
