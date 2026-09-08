package org.example.reservationservice.domain;

/** 참가자 본인용 QR 발급 응답. qrToken은 검증 API에 그대로 전달되는 값이고,
 * qrImageUrl은 goqr.me(api.qrserver.com) create-qr-code API로 qrToken을 렌더링한 이미지 주소다. */
public record ReservationQrResponseDto(
        Long reservationId,
        String qrToken,
        String qrImageUrl
) {
}
