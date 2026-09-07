package org.example.paymentservice.domain.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.dto.ApiResponse;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/payments/prepare")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> prepare(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPrepareResponse response = paymentService.prepare(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("결제 준비 완료", response));
    }
}
