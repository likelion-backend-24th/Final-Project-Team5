package org.example.paymentservice.domain.settlement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.example.paymentservice.domain.settlement.SettlementAuditLog;
import org.example.paymentservice.domain.settlement.SettlementStatus;

public record SettlementDetailResponse(
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
    String holdMessage,
    List<SettlementAdjustmentResponse> adjustments,
    long proposedPayoutAmount,
    List<SettlementLineResponse> lines,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<SettlementAuditLog> auditLogs,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String adminMemo,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String paymentReference,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String holdReason
) {}
