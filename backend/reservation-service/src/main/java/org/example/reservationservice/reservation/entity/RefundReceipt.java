package org.example.reservationservice.reservation.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** PG 취소 콜백과 API 응답이 겹쳐도 동일 취소를 한 번만 예약 수량에 반영하기 위한 기록이다. */
@Entity
@NoArgsConstructor
@Table(name = "refund_receipts")
public class RefundReceipt {
    @Id
    private String cancellationId;

    private Long reservationId;

    private int quantity;

    public RefundReceipt(String cancellationId, Long reservationId, int quantity) {
        this.cancellationId = cancellationId;
        this.reservationId = reservationId;
        this.quantity = quantity;
    }

    public boolean matches(Long id, int count) {
        return reservationId.equals(id) && quantity == count;
    }
}
