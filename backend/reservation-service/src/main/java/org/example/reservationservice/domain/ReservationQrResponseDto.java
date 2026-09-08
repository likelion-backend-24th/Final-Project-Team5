package org.example.reservationservice.domain;

import java.time.Instant;

/** 참가자 본인용 QR 발급 응답. qrToken은 검증 API에 그대로 전달되는 값이고,
 * qrImageUrl은 goqr.me(api.qrserver.com) create-qr-code API로 qrToken을 렌더링한 이미지 주소다.
 * checkInCode는 QR 스캔이 안 될 때 도우미에게 불러주는 입장 코드로, 입장 처리(checkedInAt) 후에도
 * 계속 노출된다(입장한 티켓은 프론트에서 흐리게 처리하되 이 코드는 그대로 보여준다). */
public record ReservationQrResponseDto(
        Long reservationId,
        String qrToken,
        String qrImageUrl,
        String checkInCode,
        Instant checkedInAt
) {
}
