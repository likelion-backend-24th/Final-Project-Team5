package org.example.festivalservice.domain.booth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BoothRequestDto(
        @NotNull Long festivalId,
        @NotBlank String title,
        String description,
        @NotBlank String boothHostName,
        //대표 이미지 1장(선택) — /api/store/booths/images로 먼저 업로드해 받은 URL을 그대로 실어 보낸다.
        String imageUrl
) {
}
