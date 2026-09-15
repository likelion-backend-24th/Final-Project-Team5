package org.example.paymentservice.domain.cancellation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "festival_refund_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "payment_id"})
)
public class FestivalRefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "payment_id")
    private String paymentId;
    private Long reservationId;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private FestivalRefundItemStatus status = FestivalRefundItemStatus.PENDING;
    private int retryCount;
    private String lastError;
    private String idempotencyKey;

    public FestivalRefundItem(Long batch, String payment, Long reservation) {
        batchId = batch;
        paymentId = payment;
        reservationId = reservation;
        idempotencyKey = "organizer-" + batch + "-" + payment;
    }

    public void success() {
        status = FestivalRefundItemStatus.SUCCEEDED;
        lastError = null;
    }

    public void fail() {
        status = FestivalRefundItemStatus.FAILED;
        retryCount++;
        lastError = "REFUND_RETRY_REQUIRED";
    }
}
