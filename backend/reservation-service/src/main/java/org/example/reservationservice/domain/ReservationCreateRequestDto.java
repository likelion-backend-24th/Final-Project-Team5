package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 참가자가 티켓 예매를 신청할 때 보내는 요청. */
public record ReservationCreateRequestDto(
        @NotNull Long festivalId,
        @NotNull Long ticketTypeId,
        @Positive int quantity
) {
}
