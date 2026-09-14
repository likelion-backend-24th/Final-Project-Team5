package org.example.authservice.helper.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Locale;

public record CreateHelperAccountRequest(
        @NotNull
        Long festivalId,
        @NotBlank
        @Size(max = 255)
        String festivalName,
        @NotNull
        LocalDateTime festivalStartAt,
        @NotNull
        LocalDateTime festivalEndAt,
        @NotBlank
        @Email
        @Size(max = 254)
        String email
) {
    public CreateHelperAccountRequest {
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
