package org.example.paymentservice.domain.cancellation;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor
@Table(name = "festival_refund_items", uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "payment_id"}))
public class FestivalRefundItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id") private Long batchId;
    @Column(name = "payment_id") private String paymentId;
    private Long reservationId;
    private String status = "PENDING";
    private int retryCount;
    private String lastError;
    private String idempotencyKey;
    public FestivalRefundItem(Long batch, String payment, Long reservation) {
        batchId = batch; paymentId = payment; reservationId = reservation;
        idempotencyKey = "organizer-" + batch + "-" + payment;
    }
    public void success() { status = "SUCCEEDED"; lastError = null; }
    public void fail() { status = "FAILED"; retryCount++; lastError = "REFUND_RETRY_REQUIRED"; }
}
