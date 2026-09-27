package org.example.paymentservice.domain.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.RefundPreviewService;
import org.example.paymentservice.domain.cancellation.dto.RefundPreviewResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway를 거치지 않는 내부 전용 API — 다른 서비스가 환경변수 Bearer Token으로만 호출한다.
 * festival/reservation-service 내부 API와 같이 응답은 공통 봉투(ApiResponse) 없이 그대로 내려준다.
 */
@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RefundPreviewService refundPreviewService;

    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //행사 취소 승인 전 영향 미리보기 — 페스티벌별 예상 환불 결제 수·금액(festival-service 운영자 화면용)
    @GetMapping("/refund-preview")
    public List<RefundPreviewResponse> refundPreview(
            @RequestHeader("Authorization") String authorization,
            @RequestParam("festivalIds") List<Long> festivalIds) {
        verifyInternalToken(authorization);
        return refundPreviewService.preview(festivalIds);
    }

    //토큰 비교 시간 차이로 값을 추측하지 못하도록 상수 시간 비교를 쓴다(festival-service와 같은 방식).
    private void verifyInternalToken(String authorization) {
        byte[] expected = (BEARER_PREFIX + internalAuthToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(PaymentErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}