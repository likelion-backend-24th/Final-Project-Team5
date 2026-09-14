package org.example.paymentservice.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;

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
) {}
