package org.example.festivalservice.domain.festival;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.example.festivalservice.domain.tickettype.TicketTypeRequestDto;

public record FestivalRequestDto(
        @NotBlank String name,
        String description,
        @NotNull @Future LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        //장소 — 행정구역(시/도) 드롭다운 + 상세주소 텍스트
        @NotNull FestivalRegion region,
        @NotBlank String locationDetail,
        @NotNull FestivalCategory festivalCategory,
        //입장·운영 시간(선택) — 구매자에게 보여주기만 하는 참고 정보. QR 입장 검증에는 관여하지 않는다.
        LocalTime entryStartTime,
        LocalTime operatingStartTime,
        LocalTime operatingEndTime,
        //이미지는 선택 사항 — 개수는 FestivalService에서 FestivalErrorCode로 검증한다(썸네일 0~1, 본문 0~2)
        String thumbnailImageUrl,
        List<String> detailImageUrls,
        @NotEmpty @Valid List<TicketTypeRequestDto> ticketTypes
) {
}
