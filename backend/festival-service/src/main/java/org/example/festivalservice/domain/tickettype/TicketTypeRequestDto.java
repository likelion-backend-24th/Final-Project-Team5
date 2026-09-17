package org.example.festivalservice.domain.tickettype;

import jakarta.validation.Valid;
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
        @NotNull TicketMode ticketMode,
        //SEATED일 때만 값이 있어야 한다 — 조건부 검증은 FestivalService에서 처리
        String zone,
        //SEATED일 때만 값이 있어야 한다(행별 좌석수·결번 배치)
        @Valid SeatLayout seatLayout,
        //Festival.stageLayout이 FRONT_STAGE일 때 사용 — SEATED/STANDING 둘 다 격자 위치를 가질 수 있다.
        Integer positionRow,
        Integer positionCol,
        //Festival.stageLayout이 CENTER_STAGE일 때 사용(0~359)
        Integer positionAngle,
        //STANDING일 때만 값이 있어야 한다. SEATED는 quantity 대신 seatLayout.totalSeatCount()로 서버가 계산한다.
        Integer quantity,
        //판매 시작·종료 일시(필수) — reservation-service가 예매 신청 시 이 구간을 검증한다.
        @NotNull LocalDateTime saleStartAt,
        @NotNull LocalDateTime saleEndAt,
        //이틀 이상 지속되는 페스티벌에서 특정 날짜 전용 티켓일 때만 채운다(선택)
        LocalDate ticketDate
) {
}