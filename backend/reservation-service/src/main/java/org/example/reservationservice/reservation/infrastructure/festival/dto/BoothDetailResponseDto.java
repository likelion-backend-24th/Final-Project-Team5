package org.example.reservationservice.reservation.infrastructure.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** GET /api/booths/{id} 응답에서 대기 신청 검증에 필요한 필드만 뽑아온 것. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BoothDetailResponseDto(
        Long id,
        Long festivalId,
        String boothStatus
) {
}
