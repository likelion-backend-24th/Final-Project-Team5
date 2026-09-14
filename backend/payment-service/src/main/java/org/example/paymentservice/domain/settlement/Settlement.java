package org.example.paymentservice.domain.settlement;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Getter @NoArgsConstructor
@Table(name = "settlements", uniqueConstraints = @UniqueConstraint(columnNames = {"festival_id", "test_payment"}))
public class Settlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(name = "festival_id", nullable = false) private Long festivalId;
    @Column(nullable = false) private Long hostUserId;
    private String festivalName;
    @Column(name = "test_payment", nullable = false) private boolean testPayment;
    private String currency = "KRW";
    private String feePolicyVersion = "2026-09-v1";
    private Instant eligibleAt;
    private Instant calculatedAt;
    private Instant confirmedAt;
    private Instant paidAt;
    private Instant reapprovedAt;
    private long confirmedAdjustmentAmount;
    private Long paidPayoutAmount;
    private String paymentReference;
    @Column(length = 1000) private String adminMemo;
    private String holdReason;
    private boolean manualHold;
    private long grossPaymentAmount;
    private long grossRefundedFaceAmount;
    private long customerRefundAmount;
    private long cancellationPenaltyAmount;
    private long netTicketSalesAmount;
    private long platformFeeAmount;
    private long adjustmentAmount;
    private long payoutAmount;
    @Enumerated(EnumType.STRING) @Column(columnDefinition = "VARCHAR(30)")
    private SettlementStatus status = SettlementStatus.PENDING;
    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementLine> lines = new ArrayList<>();

    public Settlement(Long festivalId, Long hostUserId, String name, Instant eligibleAt, boolean test) {
        this.festivalId = festivalId; this.hostUserId = hostUserId; this.festivalName = name;
        this.eligibleAt = eligibleAt; this.testPayment = test;
    }
    public void transition(SettlementStatus next) {
        if (!status.permits(next)) throw new IllegalStateException("SETTLEMENT_STATE_CONFLICT");
        status = next;
    }
    public void hold(String reason, boolean manual) {
        transition(SettlementStatus.HELD); holdReason = reason; manualHold = manual;
    }
    public void release() {
        if (status != SettlementStatus.HELD) throw new IllegalStateException("SETTLEMENT_STATE_CONFLICT");
        manualHold = false;
    }
    public void calculate(List<SettlementLine> newLines, long adjustment) {
        if (!status.recalculable() || manualHold) throw new IllegalStateException("RECALCULATION_BLOCKED");
        lines.addAll(newLines);
        grossPaymentAmount = 0; grossRefundedFaceAmount = 0; customerRefundAmount = 0;
        cancellationPenaltyAmount = 0; netTicketSalesAmount = 0; platformFeeAmount = 0; payoutAmount = 0;
        for (SettlementLine line : newLines) {
            grossPaymentAmount = Math.addExact(grossPaymentAmount, line.getGrossAmount());
            grossRefundedFaceAmount = Math.addExact(grossRefundedFaceAmount, line.getRefundedFaceAmount());
            customerRefundAmount = Math.addExact(customerRefundAmount, line.getCustomerRefundAmount());
            cancellationPenaltyAmount = Math.addExact(cancellationPenaltyAmount, line.getPenaltyAmount());
            netTicketSalesAmount = Math.addExact(netTicketSalesAmount, line.getGrossAmount() - line.getRefundedFaceAmount());
            platformFeeAmount = Math.addExact(platformFeeAmount, line.getFinalFeeAmount());
            payoutAmount = Math.addExact(payoutAmount, line.getPayoutAmount());
        }
        adjustmentAmount = adjustment; payoutAmount = Math.addExact(payoutAmount, adjustment);
        calculatedAt = Instant.now(); holdReason = null; transition(SettlementStatus.CALCULATED);
    }
    public void clearLines() {
        if (!status.recalculable()) throw new IllegalStateException("RECALCULATION_BLOCKED");
        lines.clear();
    }
    public void confirm() {
        if (payoutAmount < 0) throw new IllegalStateException("NEGATIVE_PAYOUT_RECEIVABLE");
        transition(SettlementStatus.CONFIRMED); confirmedAt = Instant.now();
    }
    public void markPaid(Instant at, String reference, String memo) {
        if (at == null || at.isAfter(Instant.now()) || reference == null || reference.isBlank())
            throw new IllegalArgumentException("PAYMENT_REFERENCE_REQUIRED");
        transition(SettlementStatus.PAID); paidAt = at; paymentReference = reference; adminMemo = memo;
        paidPayoutAmount = Math.addExact(payoutAmount, confirmedAdjustmentAmount);
    }
    public void reapprove(long adjustment) {
        if (paidAt != null || status != SettlementStatus.ADJUSTMENT_REQUIRED || Math.addExact(payoutAmount, adjustment) < 0)
            throw new IllegalStateException("REAPPROVAL_BLOCKED");
        transition(SettlementStatus.CONFIRMED); confirmedAdjustmentAmount = adjustment; reapprovedAt = Instant.now();
    }
}
