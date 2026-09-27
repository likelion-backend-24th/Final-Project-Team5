package org.example.festivalservice.domain.festival;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * GET /api/admin/festivals/cancellation-requests 응답 한 줄. approved=true면 이미 환불 배치가 진행 중이다.
 */
public record FestivalCancellationRequestResponseDto(
        Long festivalId,
        String name,
        //주최자가 적은 취소 사유
        String reason,
        boolean approved,
        //PENDING(대기) / REFUNDING(환불 진행 중) / CANCELLED(취소 완료) / REJECTED(반려)
        String status,
        Long hostUserId,
        //주최자 닉네임(auth-service 조회, 실패하면 null)
        String hostNickname,
        LocalDateTime startAt,
        LocalDateTime endAt,
        //운영자 승인 시각(환불 진행 중·취소 완료)
        Instant approvedAt,
        //취소 완료 시각(모든 환불 완료)
        Instant cancelledAt,
        //반려 시각(REJECTED만)
        LocalDateTime rejectedAt,
        //영향 미리보기 — 대기(PENDING)에서만 채운다
        //판매 티켓 수(티켓 재고 기준: 전체 - 남은 수량)
        Long soldQuantity,
        //환불이 나갈 결제 수(payment-service 기준)
        Long refundTargetPaymentCount,
        //예상 환불 금액
        Long expectedRefundAmount,
        //금액을 계산할 수 없는 결제 수
        Long unresolvedPaymentCount
) {
}