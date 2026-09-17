package org.example.reservationservice.seat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * festival-service의 SeatLayout과 구조가 정확히 일치해야 한다. 서로 다른 서비스라 클래스를
 * 공유할 수 없어 각자 독립적으로 갖고 있다 — festival-service 쪽 구조가 바뀌면 이쪽도 수동으로 맞춰야 한다.
 */
public record SeatLayout(
        @NotNull @NotEmpty @Valid List<RowLayout> rows
) {
    public record RowLayout(
            @Min(1) int seatCount,
            List<Integer> excludedSeats
    ) {
        public RowLayout {
            if (excludedSeats == null) excludedSeats = List.of();
        }
    }
}