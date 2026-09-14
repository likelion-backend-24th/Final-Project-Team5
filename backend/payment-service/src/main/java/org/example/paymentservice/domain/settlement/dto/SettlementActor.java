package org.example.paymentservice.domain.settlement.dto;

import java.time.Instant;
import java.util.Set;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.settlement.SettlementErrorCode;
import org.example.paymentservice.domain.settlement.SettlementStatus;

public record SettlementActor(Long id, String role) {
        public void require(String expected) {
            if (id == null || !expected.equals(role)) throw new ApiException(SettlementErrorCode.FORBIDDEN_ROLE);
        }
    }
