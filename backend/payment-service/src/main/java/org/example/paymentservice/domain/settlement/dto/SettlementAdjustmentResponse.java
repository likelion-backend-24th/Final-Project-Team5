package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;

public record SettlementAdjustmentResponse(
    long amount,
    long remainingAmount,
    String status,
    String kind,
    Instant createdAt
) {}
