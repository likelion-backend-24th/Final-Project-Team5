package org.example.reservationservice.domain.seat;

public enum SeatStatus {
    AVAILABLE,
    //선점(hold) 상태 — heldBy/heldUntil이 채워진다. 결제 완료 전 임시 상태.
    HELD,
    //결제 확정된 좌석
    SOLD
}