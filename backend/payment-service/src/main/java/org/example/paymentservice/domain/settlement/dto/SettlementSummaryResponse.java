package org.example.paymentservice.domain.settlement.dto;

public record SettlementSummaryResponse(
        String currency,
        String dateBasis,
        int count,
        long grossPaymentAmount,
        long customerRefundAmount,
        long platformFeeAmount,
        long payoutAmount,
        long paidAmount,
        long heldCount,
        long scheduledAmount,
        long reviewCount
) {
}
