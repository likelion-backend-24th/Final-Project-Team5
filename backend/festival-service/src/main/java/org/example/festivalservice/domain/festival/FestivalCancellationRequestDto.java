package org.example.festivalservice.domain.festival;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/host/festivals/{id}/cancellation-request 요청 — 주최자가 남기는 취소 사유(운영자 승인 화면에 그대로 보인다). */
public record FestivalCancellationRequestDto(
        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 500, message = "취소 사유는 500자 이내여야 합니다.")
        String reason
) {
}
