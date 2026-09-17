package org.example.festivalservice.domain.tickettype;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketTypeResponseDto(
        Long id,
        String name,
        String description,
        int price,
        TicketMode ticketMode,
        String zone,
        SeatLayout seatLayout,
        Integer positionRow,
        Integer positionCol,
        Integer positionAngle,
        int totalQuantity,
        int remainQuantity,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        LocalDate ticketDate
) {
    public static TicketTypeResponseDto from(TicketType ticketType) {
        return new TicketTypeResponseDto(
                ticketType.getId(),
                ticketType.getName(),
                ticketType.getDescription(),
                ticketType.getPrice(),
                ticketType.getTicketMode(),
                ticketType.getZone(),
                ticketType.getSeatLayout(),
                ticketType.getPositionRow(),
                ticketType.getPositionCol(),
                ticketType.getPositionAngle(),
                ticketType.getTotalQuantity(),
                ticketType.getRemainQuantity(),
                ticketType.getSaleStartAt(),
                ticketType.getSaleEndAt(),
                ticketType.getTicketDate()
        );
    }
}