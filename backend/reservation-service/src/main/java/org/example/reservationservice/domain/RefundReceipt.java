package org.example.reservationservice.domain;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Table(name = "refund_receipts")
public class RefundReceipt {
    @Id private String cancellationId;
    private Long reservationId;
    private int quantity;
    public RefundReceipt(String cancellationId, Long reservationId, int quantity) {
        this.cancellationId = cancellationId;
        this.reservationId = reservationId;
        this.quantity = quantity;
    }
    public boolean matches(Long id, int count) { return reservationId.equals(id) && quantity == count; }
}
