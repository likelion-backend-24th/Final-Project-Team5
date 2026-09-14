package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.List;
import org.example.paymentservice.domain.settlement.SettlementStatus;
import org.example.paymentservice.domain.settlement.SettlementAuditLog;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

public record SettlementLineResponse(
        PaymentMethodCategory paymentMethod,
        Instant paidAt,
        long grossAmount,
        long refundedFaceAmount,
        long customerRefundAmount,
        long penaltyAmount,
        int feeRateBps,
        long initialFeeAmount,
        long feeReversalAmount,
        long finalFeeAmount,
        long payoutAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long paymentId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long reservationId
) {

}
