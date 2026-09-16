package org.example.reservationservice.seat.dto;

import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.entity.Seat;

public record SeatResponse(
        Long id,
        String zone,
        String rowLabel,
        int seatNumber,
        SeatStatus seatStatus
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getZone(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getSeatStatus()
        );
    }
}