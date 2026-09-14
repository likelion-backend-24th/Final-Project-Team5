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
    @JsonInclude(JsonInclude.Include.NON_NULL) List<SettlementAuditLog> auditLogs,
    @JsonInclude(JsonInclude.Include.NON_NULL) String adminMemo,
    @JsonInclude(JsonInclude.Include.NON_NULL) String paymentReference,
    @JsonInclude(JsonInclude.Include.NON_NULL) String holdReason
) {
    // 기존 서비스 호출자의 문자열 키 조회를 유지하면서 HTTP 응답은 record로 직렬화한다.
    public Object get(String key) {
        return switch (key) {
            case "id" -> id;
            case "version" -> version;
            case "festivalId" -> festivalId;
            case "festivalName" -> festivalName;
            case "hostUserId" -> hostUserId;
            case "currency" -> currency;
            case "status" -> status;
            case "hostName" -> hostName;
            case "manualHold" -> manualHold;
            case "eligibleAt" -> eligibleAt;
            case "calculatedAt" -> calculatedAt;
            case "confirmedAt" -> confirmedAt;
            case "paidAt" -> paidAt;
            case "grossPaymentAmount" -> grossPaymentAmount;
            case "grossRefundedFaceAmount" -> grossRefundedFaceAmount;
            case "customerRefundAmount" -> customerRefundAmount;
            case "cancellationPenaltyAmount" -> cancellationPenaltyAmount;
            case "netTicketSalesAmount" -> netTicketSalesAmount;
            case "platformFeeAmount" -> platformFeeAmount;
            case "adjustmentAmount" -> adjustmentAmount;
            case "payoutAmount" -> payoutAmount;
            case "confirmedAdjustmentAmount" -> confirmedAdjustmentAmount;
            case "payableAmount" -> payableAmount;
            case "paidPayoutAmount" -> paidPayoutAmount;
            case "reapprovedAt" -> reapprovedAt;
            case "holdMessage" -> holdMessage;
            case "adjustments" -> adjustments;
            case "proposedPayoutAmount" -> proposedPayoutAmount;
            case "lines" -> lines;
            case "auditLogs" -> auditLogs;
            case "adminMemo" -> adminMemo;
            case "paymentReference" -> paymentReference;
            case "holdReason" -> holdReason;
            default -> null;
        };
    }
}
