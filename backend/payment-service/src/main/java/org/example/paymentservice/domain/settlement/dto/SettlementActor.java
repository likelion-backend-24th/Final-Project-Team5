package org.example.paymentservice.domain.settlement.dto;

import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.settlement.SettlementErrorCode;

public record SettlementActor(Long id, String role) {
    public void require(String expected) {
        if (id == null || !expected.equals(role)) {
            throw new ApiException(SettlementErrorCode.FORBIDDEN_ROLE);
        }
    }
}
