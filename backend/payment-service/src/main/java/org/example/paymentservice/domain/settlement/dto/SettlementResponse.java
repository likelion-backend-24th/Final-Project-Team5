package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.List;
import org.example.paymentservice.domain.settlement.SettlementStatus;
import org.example.paymentservice.domain.settlement.SettlementAuditLog;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

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
