package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;

import org.example.paymentservice.domain.settlement.SettlementStatus;

public record SettlementResponse(
        Long id,
        Long version,
        Long festivalId,
        String festivalName,
        Long hostUserId,
        String currency,
        SettlementStatus status,
        String hostName,
        boolean manualHold,
        Instant eligibleAt,
        Instant calculatedAt,
        Instant confirmedAt,
        Instant paidAt,
        long grossPaymentAmount,
        long grossRefundedFaceAmount,
        long customerRefundAmount,
        long cancellationPenaltyAmount,
        long netTicketSalesAmount,
        long platformFeeAmount,
        long adjustmentAmount,
        long payoutAmount,
        long confirmedAdjustmentAmount,
        long payableAmount,
        Long paidPayoutAmount,
        Instant reapprovedAt,
        String holdMessage
) {
}
