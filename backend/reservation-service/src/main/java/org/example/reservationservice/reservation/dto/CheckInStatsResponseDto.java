package org.example.reservationservice.domain;

/**
 * 도우미·주최자 현장 화면에 띄우는 입장 현황. 총 티켓 수는 결제가 확정된 예매의 수량 합계이고,
 * 입장 인원은 그중 이미 입장 처리된 예매의 수량 합계다.
 */
public record CheckInStatsResponseDto(
        Long festivalId,
        int totalTickets,
        int checkedInTickets
) {
}
