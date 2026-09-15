package org.example.authservice.helper.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HelperAccountSummaryResponse(
        int totalCount,
        List<HelperAccount> helpers
) {
    public record HelperAccount(
            Long helperUserId,
            String username,
            String email,
            String status,
            String deliveryStatus,
            LocalDateTime sentAt,
            LocalDateTime lastSentAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            boolean legacy
    ) {
    }
}
