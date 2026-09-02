package org.example.festivalservice.domain.hostapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HostApplicationSubmitRequestDto(
        @NotBlank @Size(max = 1000) String introduction,
        @NotBlank @Size(max = 255) String contact
) {
}
