package org.example.festivalservice.domain.festival;

/**
 * GET /api/admin/festivals/cancellation-requests 응답 한 줄. approved=true면 이미 환불 배치가 진행 중이다.
 */
public record FestivalCancellationRequestResponseDto(Long festivalId, String name, String reason, boolean approved) {
}
