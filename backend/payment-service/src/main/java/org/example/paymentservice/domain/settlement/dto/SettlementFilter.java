package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.Set;

import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import org.example.paymentservice.domain.settlement.SettlementErrorCode;
import org.example.paymentservice.domain.settlement.SettlementStatus;

public record SettlementFilter(
        Instant from,
        Instant to,
        SettlementStatus status,
        Long festivalId,
        Long hostUserId,
        PaymentMethodCategory paymentMethod,
        boolean testPayment,
        String dateBasis,
        String festivalName,
        String hostName
) {
    public SettlementFilter(
            Instant from,
            Instant to,
            SettlementStatus status,
            Long festivalId,
            Long hostUserId,
            PaymentMethodCategory paymentMethod,
            boolean testPayment,
            String dateBasis
    ) {
        this(from, to, status, festivalId, hostUserId, paymentMethod, testPayment, dateBasis, null, null);
    }

    public SettlementFilter(
            Instant from,
            Instant to,
            SettlementStatus status,
            Long festivalId,
            Long hostUserId,
            PaymentMethodCategory paymentMethod,
            boolean testPayment
    ) {
        this(from, to, status, festivalId, hostUserId, paymentMethod, testPayment, "SETTLEMENT_AT");
    }

    public SettlementFilter {
        if (!Set.of("PAID_AT", "SETTLEMENT_AT").contains(dateBasis)) {
            throw new ApiException(SettlementErrorCode.INVALID_FILTER);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(SettlementErrorCode.INVALID_FILTER);
        }
    }
}
