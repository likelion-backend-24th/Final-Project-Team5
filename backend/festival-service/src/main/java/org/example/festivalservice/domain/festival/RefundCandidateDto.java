package org.example.festivalservice.domain.festival;

public record RefundCandidateDto(Long festivalId, Long hostUserId, Long initiatedBy, String reason) {}
