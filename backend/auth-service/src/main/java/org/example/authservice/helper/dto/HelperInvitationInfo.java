package org.example.authservice.helper.dto;

import java.time.LocalDateTime;
public record HelperInvitationInfo(String username, Long festivalId, String festivalName,
        LocalDateTime festivalStartAt, LocalDateTime festivalEndAt, LocalDateTime expiresAt, String maskedEmail) { }
