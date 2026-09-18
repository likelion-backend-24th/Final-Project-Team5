package org.example.festivalservice.domain.tickettype;

import java.util.List;

/**
 * SEATED 티켓타입의 행별 좌석 배치. TicketType.seatLayout 컬럼에 JSON 문자열로 저장된다.
 * 행마다 좌석 수가 다를 수 있고(불규칙한 행), 통로·기둥 등으로 특정 좌석 번호를 결번 처리할 수 있다.
 */
public record SeatLayout(List<RowLayout> rows) {

    public record RowLayout(int seatCount, List<Integer> excludedSeats) {
        public RowLayout {
            if (excludedSeats == null) excludedSeats = List.of();
        }
    }

    //실제 생성될 좌석 수(결번 제외) 총합 — totalQuantity/remainQuantity 자동 계산에 쓰인다.
    public int totalSeatCount() {
        return rows.stream()
                .mapToInt(row -> row.seatCount() - row.excludedSeats().size())
                .sum();
    }
}