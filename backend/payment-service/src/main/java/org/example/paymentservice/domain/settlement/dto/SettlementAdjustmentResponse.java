package org.example.paymentservice.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;

@JsonPropertyOrder({ "createdAt", "amount", "remainingAmount", "status", "kind" })
public record SettlementAdjustmentResponse(
    long amount,
    long remainingAmount,
    String status,
    String kind,
    Instant createdAt
) {}
