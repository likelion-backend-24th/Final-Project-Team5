package org.example.reservationservice.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 참가자가 티켓 예매를 신청할 때 보내는 요청.
 * SEATED 티켓타입이면 seatIds를 채우고(quantity는 무시, seatIds.size()로 대체),
 * STANDING이면 기존처럼 quantity만 채운다(seatIds는 null).
 */
public record ReservationCreateRequestDto(
        @NotNull Long festivalId,
        @NotNull Long ticketTypeId,
        //STANDING일 때만 사용
        Integer quantity,
        //SEATED일 때만 사용 — 선택한 특정 좌석들의 id
        List<Long> seatIds
) {
}