package org.example.festivalservice.domain.festival;

import java.util.List;

public record FestivalImageUploadResponseDto(
        String thumbnailImageUrl,
        List<String> detailImageUrls
) {
}
