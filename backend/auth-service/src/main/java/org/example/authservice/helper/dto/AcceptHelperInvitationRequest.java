package org.example.authservice.helper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.authservice.auth.dto.PasswordPolicy;

public record AcceptHelperInvitationRequest(
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        String password,
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
        String passwordConfirm
) {
    @Override
    public String toString() {
        return "AcceptHelperInvitationRequest[REDACTED]";
    }
}
