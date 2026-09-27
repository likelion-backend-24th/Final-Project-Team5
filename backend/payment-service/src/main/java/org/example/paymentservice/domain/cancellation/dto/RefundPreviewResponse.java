package org.example.paymentservice.domain.cancellation.dto;

/**
 * 행사 취소 환불 미리보기 — 페스티벌 하나의 예상 환불 정보.
 * 금액 = 결제 금액 - 이미 성공한 환불 액면가 (실제 행사 취소 환불과 같은 기준, 위약금 없음)
 */
public record RefundPreviewResponse(
        Long festivalId,
        //환불이 실제로 나갈 결제 수(남은 금액이 있는 결제만)
        long refundTargetPaymentCount,
        //예상 환불 금액 합계
        long expectedRefundAmount,
        //외부 취소 등으로 액면가 기록이 없어 금액을 계산할 수 없는 결제 수(금액 합계에서 제외)
        long unresolvedPaymentCount
) {
}