package org.example.festivalservice.domain.tickettype;

public record TicketTypeResponseDto(
        Long id,
        String name,
        int price,
        int totalQuantity,
        int remainQuantity
) {
    public static TicketTypeResponseDto from(TicketType ticketType) {
        return new TicketTypeResponseDto(
                ticketType.getId(),
                ticketType.getName(),
                ticketType.getPrice(),
                ticketType.getTotalQuantity(),
                ticketType.getRemainQuantity()
        );
    }
}
