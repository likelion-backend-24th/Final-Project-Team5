package org.example.reservationservice.boothwaitlist.dto;

import java.time.Instant;
import org.example.reservationservice.boothwaitlist.entity.BoothWaitlist;

public record BoothWaitlistResponseDto(
        Long id,
        Long boothId,
        int queueNumber,
        Instant requestedAt
) {
    public static BoothWaitlistResponseDto from(BoothWaitlist waitlist) {
        return new BoothWaitlistResponseDto(
                waitlist.getId(),
                waitlist.getBoothId(),
                waitlist.getQueueNumber(),
                waitlist.getRequestedAt()
        );
    }
}
