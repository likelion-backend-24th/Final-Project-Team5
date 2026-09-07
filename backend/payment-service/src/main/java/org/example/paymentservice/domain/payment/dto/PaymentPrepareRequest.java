package org.example.paymentservice.domain.payment.dto;

import jakarta.validation.constraints.NotNull;

public record PaymentPrepareRequest(
        @NotNull Long reservationId
) {
}
