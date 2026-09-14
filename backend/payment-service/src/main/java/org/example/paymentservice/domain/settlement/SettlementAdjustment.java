package org.example.paymentservice.domain.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
    name = "settlement_adjustments",
    uniqueConstraints = @UniqueConstraint(
        columnNames = { "source_settlement_id", "payment_id", "refunded_face_amount", "customer_refund_amount" }
    )
)
public class SettlementAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_settlement_id")
    private Long sourceSettlementId;

    @Column(name = "payment_id")
    private Long paymentId;

    private Long hostUserId;

    private boolean testPayment;

    @Column(name = "refunded_face_amount")
    private long refundedFaceAmount;

    @Column(name = "customer_refund_amount")
    private long customerRefundAmount;

    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private SettlementAdjustmentKind kind;

    private long remainingAmount;

    @Version
    private Long version;

    private Instant createdAt = Instant.now();

    public SettlementAdjustment(Settlement source, Long paymentId, long face, long cash, long amount) {
        sourceSettlementId = source.getId();
        hostUserId = source.getHostUserId();
        testPayment = source.isTestPayment();
        this.paymentId = paymentId;
        refundedFaceAmount = face;
        customerRefundAmount = cash;
        this.amount = amount;
        kind =
            source.getPaidAt() == null ? SettlementAdjustmentKind.PRE_PAYMENT : SettlementAdjustmentKind.POST_PAYMENT;
        remainingAmount = source.getPaidAt() == null ? 0 : amount;
    }

    public void allocate(long applied) {
        if (
            applied != 0 &&
            (Long.signum(applied) != Long.signum(remainingAmount) || Math.abs(applied) > Math.abs(remainingAmount))
        ) throw new IllegalArgumentException("INVALID_ADJUSTMENT_ALLOCATION");
        remainingAmount = Math.subtractExact(remainingAmount, applied);
    }

    public void restore(long applied) {
        remainingAmount = Math.addExact(remainingAmount, applied);
    }

    public String getStatus() {
        return kind == SettlementAdjustmentKind.PRE_PAYMENT
            ? "PRE_PAYMENT"
            : remainingAmount != 0
              ? "RECEIVABLE"
              : "ALLOCATED";
    }
}
