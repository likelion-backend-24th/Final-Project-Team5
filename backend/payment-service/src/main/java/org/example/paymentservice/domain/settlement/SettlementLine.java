package org.example.paymentservice.domain.settlement;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;

@Entity @Getter @NoArgsConstructor
@Table(name = "settlement_lines", uniqueConstraints = @UniqueConstraint(columnNames = {"settlement_id", "payment_id"}))
public class SettlementLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    private Long reservationId;
    private Long ticketTypeId;
    private Instant paidAt;
    @Enumerated(EnumType.STRING) @Column(columnDefinition = "VARCHAR(30)") private PaymentMethodCategory paymentMethod;
    private int feeRateBps;
    private long grossAmount;
    private long refundedFaceAmount;
    private long customerRefundAmount;
    private long penaltyAmount;
    private long initialFeeAmount;
    private long feeReversalAmount;
    private long finalFeeAmount;
    private long payoutAmount;
    public SettlementLine(Settlement settlement, Long paymentId, Long reservationId, Long ticketTypeId,
                          Instant paidAt, PaymentMethodCategory method, SettlementCalculator.Result result) {
        this.settlement = settlement; this.paymentId = paymentId; this.reservationId = reservationId;
        this.ticketTypeId = ticketTypeId; this.paidAt = paidAt; this.paymentMethod = method;
        this.feeRateBps = method.rateBps(); this.grossAmount = result.gross();
        this.refundedFaceAmount = result.face(); this.customerRefundAmount = result.cash();
        this.penaltyAmount = result.penalty(); this.initialFeeAmount = result.initialFee();
        this.feeReversalAmount = result.reversal(); this.finalFeeAmount = result.fee(); this.payoutAmount = result.payout();
    }
}
