package org.example.festivalservice.domain.tickettype;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketTypeResponseDto(
        Long id,
        String name,
        String description,
        int price,
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
                ticketType.getTotalQuantity(),
                ticketType.getRemainQuantity(),
                ticketType.getSaleStartAt(),
                ticketType.getSaleEndAt(),
                ticketType.getTicketDate()
        );
    }
}
