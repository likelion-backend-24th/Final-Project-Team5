package org.example.reservationservice.domain.seat;

public record SeatResponseDto(
        Long id,
        String zone,
        String rowLabel,
        int seatNumber,
        SeatStatus seatStatus
) {
    public static SeatResponseDto from(Seat seat) {
        return new SeatResponseDto(
                seat.getId(),
                seat.getZone(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatStatus()
        );
    }
}