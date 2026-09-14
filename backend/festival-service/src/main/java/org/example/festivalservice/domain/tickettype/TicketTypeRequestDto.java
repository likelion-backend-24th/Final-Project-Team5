package org.example.festivalservice.domain.tickettype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketTypeRequestDto(
        @NotBlank String name,
        //구매 화면에서 티켓 이름 아래 보여줄 한 줄 설명(선택)
        @Size(max = 50, message = "티켓 설명은 50자 이내로 입력해주세요.") String description,
        @PositiveOrZero int price,
        @Positive int quantity,
        //판매 시작·종료 일시(필수) — reservation-service가 예매 신청 시 이 구간을 검증한다.
        @NotNull LocalDateTime saleStartAt,
        @NotNull LocalDateTime saleEndAt,
        //이틀 이상 지속되는 페스티벌에서 특정 날짜 전용 티켓일 때만 채운다(선택)
        LocalDate ticketDate
) {
}
