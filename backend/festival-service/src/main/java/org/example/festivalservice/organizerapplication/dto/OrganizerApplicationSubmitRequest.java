package org.example.festivalservice.organizerapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizerApplicationSubmitRequest(
        @NotBlank @Size(max = 1000) String introduction,
        @NotBlank @Size(max = 255) String contact
) {
}
