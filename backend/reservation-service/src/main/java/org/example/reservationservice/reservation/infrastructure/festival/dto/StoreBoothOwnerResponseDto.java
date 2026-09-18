package org.example.reservationservice.reservation.infrastructure.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /api/store/booths/{id} 응답에서 대기 호출 소유권 검증에 필요한 필드만 뽑아온 것. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreBoothOwnerResponseDto(
        Long id,
        Long festivalId,
        Long hostUserId
) {
}
