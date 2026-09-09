package org.example.paymentservice.domain.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.example.paymentservice.common.dto.ApiResponse;
import org.example.paymentservice.domain.cancellation.PaymentCancellationService;
import org.example.paymentservice.domain.cancellation.dto.PaymentCancellationRequest;
import org.example.paymentservice.domain.cancellation.dto.PaymentCancellationResponse;
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
    private final PaymentCancellationService paymentCancellationService;

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

    // 전체·부분 환불(Story 9). 실전 가이드 5.4의 경로·헤더 계약을 그대로 따른다.
    // 환불 금액을 본문으로 받지 않는 이유는 결제 완료와 같다 — 위약금 계산은 서버만 신뢰한다.
    @PostMapping("/api/payments/{paymentId}/cancellations")
    public ResponseEntity<ApiResponse<PaymentCancellationResponse>> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) PaymentCancellationRequest request) {
        PaymentCancellationRequest body =
                request != null ? request : new PaymentCancellationRequest(null, null);
        // 클라이언트가 키를 안 보내면 재시도 보호를 포기하는 대신 요청을 막지는 않는다.
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();

        PaymentCancellationResponse response =
                paymentCancellationService.cancel(userId, paymentId, body.quantity(), body.reason(), key);
        return ResponseEntity.ok(ApiResponse.success("환불 요청 완료", response));
    }
}
