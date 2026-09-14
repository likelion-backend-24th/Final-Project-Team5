package org.example.festivalservice.infrastructure.reservation.dto;

/**
 * Festival-Service → Reservation-Service: POST /internal/v1/seats 요청 바디.
 * SEATED 티켓타입 하나당 이 요청을 한 번씩 보내 실제 좌석 로우를 생성하게 한다.
 * reservation-service 쪽이 같은 ticketTypeId로 이미 생성된 좌석이 있으면 재생성하지 않고
 * 멱등하게 200을 반환하므로, 재시도 스케줄러가 여러 번 호출해도 안전하다.
 */
public record SeatGenerationRequestDto(
        Long festivalId,
        Long ticketTypeId,
        String zone,
        Integer rows,
        Integer seatsPerRow
) {
}