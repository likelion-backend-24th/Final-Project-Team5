package org.example.festivalservice.domain.helper;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class HelperAccountDto {
    private HelperAccountDto() { }
    public record InviteRequest(@NotBlank @Email @Size(max = 254) String email) {
        public InviteRequest { email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    }
    public record CreateRequest(Long festivalId, String festivalName, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime festivalStartAt, @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime festivalEndAt, String email) { }
    public record HelperAccount(Long helperUserId, String username, String email, String status,
        String deliveryStatus, LocalDateTime sentAt, LocalDateTime lastSentAt, LocalDateTime expiresAt,
        LocalDateTime createdAt, boolean legacy) { }
    public record SummaryResponse(int totalCount, List<HelperAccount> helpers) { }
}
