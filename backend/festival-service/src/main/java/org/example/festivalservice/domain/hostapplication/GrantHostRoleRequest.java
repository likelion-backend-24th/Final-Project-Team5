package org.example.festivalservice.domain.hostapplication;

/** PUT /internal/v1/roles 요청 바디. applicationId는 멱등키. */
public record GrantHostRoleRequest(Long userId, Long applicationId, String role) {
}
