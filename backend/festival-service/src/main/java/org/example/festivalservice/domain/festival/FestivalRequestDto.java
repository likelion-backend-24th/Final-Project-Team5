package org.example.festivalservice.domain.festival;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.domain.tickettype.TicketTypeRequestDto;

public record FestivalRequestDto(
        @NotBlank String name,
        String description,
        @NotNull @Future LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotBlank String location,
        @NotNull FestivalCategory festivalCategory,
        //이미지는 선택 사항 — 개수는 FestivalService에서 FestivalErrorCode로 검증한다(썸네일 0~1, 본문 0~2)
        String thumbnailImageUrl,
        List<String> detailImageUrls,
        @NotEmpty @Valid List<TicketTypeRequestDto> ticketTypes
) {
}
