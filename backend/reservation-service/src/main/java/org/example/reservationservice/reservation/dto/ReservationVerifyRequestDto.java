package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotBlank;

/** 주최자가 현장에서 스캔한 QR 원문(qrToken)을 그대로 담아 보내는 검증 요청. */
public record ReservationVerifyRequestDto(
        @NotBlank String qrToken
) {
}
