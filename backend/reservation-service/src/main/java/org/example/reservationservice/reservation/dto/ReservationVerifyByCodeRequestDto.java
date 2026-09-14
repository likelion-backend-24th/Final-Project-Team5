package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotBlank;

/** QR 스캔이 실패했을 때 도우미가 손으로 입력한 입장 코드(2-4-4)를 담아 보내는 검증 요청. */
public record ReservationVerifyByCodeRequestDto(
        @NotBlank String checkInCode
) {
}
