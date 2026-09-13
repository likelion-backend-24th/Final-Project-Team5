package org.example.festivalservice.domain.festival;

import jakarta.validation.constraints.NotNull;

public record FestivalReviewRequestDto(
        @NotNull FestivalStatus decision,
        //REJECTED일 때 필수. 주최자 신청 반려와 같은 방식으로 주최자에게 그대로 전달된다
        String rejectReason
) {
}
