package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.List;
import org.example.paymentservice.domain.settlement.SettlementStatus;
import org.example.paymentservice.domain.settlement.SettlementAuditLog;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

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
