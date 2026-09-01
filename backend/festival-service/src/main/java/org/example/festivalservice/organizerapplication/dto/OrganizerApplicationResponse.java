package org.example.festivalservice.organizerapplication.dto;

import java.time.LocalDateTime;
import org.example.festivalservice.organizerapplication.OrganizerApplication;
import org.example.festivalservice.organizerapplication.OrganizerApplicationStatus;

public record OrganizerApplicationResponse(
        Long id,
        OrganizerApplicationStatus status,
        String introduction,
        String contact,
        String rejectReason,
        LocalDateTime createdAt
) {
    public static OrganizerApplicationResponse from(OrganizerApplication application) {
        return new OrganizerApplicationResponse(
                application.getId(),
                application.getStatus(),
                application.getIntroduction(),
                application.getContact(),
                application.getRejectReason(),
                application.getCreatedAt()
        );
    }
}
