package org.example.authservice.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Festival-Service -> Auth-Service 내부 계약: PUT /internal/v1/roles. applicationId가 멱등키. */
public record GrantRoleRequest(
        @NotNull Long userId,
        @NotNull Long applicationId,
        @NotBlank String role
) {
}
