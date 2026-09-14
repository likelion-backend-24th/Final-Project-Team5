package org.example.festivalservice.domain.festival;

/** GET /internal/v1/festivals/refund-candidates 응답 — 운영자가 승인한 행사 취소 건. 환불 배치의 입력이다. */
public record RefundCandidateDto(Long festivalId, Long hostUserId, Long initiatedBy, String reason) {
}
