package org.example.paymentservice.domain.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.dto.ApiResponse;
import org.example.paymentservice.domain.payment.dto.PaymentCompleteResponse;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
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

    // 브라우저 결제 결과를 서버가 재검증하고 예매를 확정·취소한다. 요청 본문에 프론트 결제 상태·금액을 받지 않는다
    // (실전 가이드 5.3 — paymentId와 로그인 사용자만으로 PortOne을 다시 조회해 확정한다).
    @PostMapping("/api/payments/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PaymentCompleteResponse>> complete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentId) {
        PaymentCompleteResponse response = paymentService.complete(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success("결제 확인 완료", response));
    }
}
