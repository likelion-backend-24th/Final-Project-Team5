package org.example.festivalservice.domain.hostapplication;

import java.time.LocalDateTime;

public record HostApplicationResponseDto(
        Long id,
        HostApplicationStatus status,
        String introduction,
        String contact,
        String rejectReason,
        LocalDateTime createdAt
) {
    public static HostApplicationResponseDto from(HostApplication application) {
        return new HostApplicationResponseDto(
                application.getId(),
                application.getStatus(),
                application.getIntroduction(),
                application.getContact(),
                application.getRejectReason(),
                application.getCreatedAt()
        );
    }
}
