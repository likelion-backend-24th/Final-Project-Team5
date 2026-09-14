package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.List;
import org.example.paymentservice.domain.settlement.SettlementStatus;
import org.example.paymentservice.domain.settlement.SettlementAuditLog;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

public record SettlementAdjustmentResponse(
        long amount,
        long remainingAmount,
        String status,
        String kind,
        Instant createdAt
) {

}
