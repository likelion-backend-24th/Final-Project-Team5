package org.example.paymentservice.domain.cancellation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.domain.cancellation.dto.RefundPreviewResponse;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 취소 승인 전 영향 미리보기 — 실제 환불을 하지 않고 예상 환불 결제 수·금액만 계산한다.
 * 계산식은 행사 취소 환불(organizerRefund)과 같다: 결제 금액 - 이미 성공한 환불의 액면가 합계.
 */
@Service
@RequiredArgsConstructor
public class RefundPreviewService {

    //행사 취소 환불 배치와 같은 대상 (CANCELLED는 남은 금액이 0이라 제외해도 결과가 같다)
    private static final List<PaymentStatus> TARGET_STATUSES =
            List.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED);

    //한 번에 받을 수 있는 최대 페스티벌 수
    private static final int MAX_FESTIVAL_IDS = 50;

    private final PaymentRepository paymentRepository;
    private final CancellationRepository cancellationRepository;

    @Transactional(readOnly = true)
    public List<RefundPreviewResponse> preview(List<Long> festivalIds) {
        List<Long> ids = festivalIds.stream().distinct().limit(MAX_FESTIVAL_IDS).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        // 결제 id → [이미 성공한 환불 액면가 합계, 액면가 없는 취소 건수]
        Map<Long, long[]> refundedByPayment = new HashMap<>();
        for (Object[] row : cancellationRepository.sumGrossByFestivalIds(ids, CancellationStatus.SUCCEEDED)) {
            Long paymentId = (Long) row[0];
            long grossSum = row[1] == null ? 0L : ((Number) row[1]).longValue();
            long missingGross = row[2] == null ? 0L : ((Number) row[2]).longValue();
            refundedByPayment.put(paymentId, new long[]{grossSum, missingGross});
        }

        // 페스티벌 id → [환불 대상 결제 수, 예상 환불 금액, 계산 불가 결제 수] (요청 순서 유지, 0으로 시작)
        Map<Long, long[]> summaryByFestival = new LinkedHashMap<>();
        for (Long id : ids) {
            summaryByFestival.put(id, new long[]{0L, 0L, 0L});
        }

        for (Object[] row : paymentRepository.findRefundPreviewTargets(ids, TARGET_STATUSES)) {
            Long paymentId = (Long) row[0];
            Long festivalId = (Long) row[1];
            long ticketAmount = ((Number) row[2]).longValue();

            long[] summary = summaryByFestival.get(festivalId);
            if (summary == null) {
                continue;
            }

            long[] refunded = refundedByPayment.getOrDefault(paymentId, new long[]{0L, 0L});

            // 액면가 없는 취소가 섞여 있으면 정확한 남은 금액을 알 수 없다 → 금액에서 빼고 개수만 센다
            if (refunded[1] > 0) {
                summary[2]++;
                continue;
            }

            long remaining = ticketAmount - refunded[0];
            if (remaining <= 0) {
                continue;
            }
            summary[0]++;
            summary[1] += remaining;
        }

        List<RefundPreviewResponse> result = new ArrayList<>();
        for (Map.Entry<Long, long[]> entry : summaryByFestival.entrySet()) {
            long[] summary = entry.getValue();
            result.add(new RefundPreviewResponse(entry.getKey(), summary[0], summary[1], summary[2]));
        }
        return result;
    }
}