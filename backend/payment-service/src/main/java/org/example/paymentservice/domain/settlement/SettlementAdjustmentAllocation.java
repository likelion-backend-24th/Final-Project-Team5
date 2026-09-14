package org.example.paymentservice.domain.settlement;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @NoArgsConstructor
@Table(name = "settlement_adjustment_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"adjustment_id", "settlement_id"}))
public class SettlementAdjustmentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "adjustment_id") private Long adjustmentId;
    @Column(name = "settlement_id") private Long settlementId;
    private long amount;
    public SettlementAdjustmentAllocation(Long adjustment, Long settlement, long amount) {
        adjustmentId = adjustment; settlementId = settlement; this.amount = amount;
    }
    public void releaseDebt(long value) {
        if (value < 0 || amount >= 0 || value > -amount) throw new IllegalArgumentException("INVALID_DEBT_RELEASE");
        amount = Math.addExact(amount, value);
    }
}
