package org.example.paymentservice.domain.cancellation;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.example.paymentservice.domain.settlement.FestivalSettlementClient;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "festival-refund.scheduling-enabled", matchIfMissing = true)
public class FestivalRefundScheduler {

    //승인된 취소 결제만 환불 대상이다 — READY/FAILED 결제는 돌려줄 돈이 없다.
    private static final Set<PaymentStatus> REFUNDABLE_STATUSES =
            Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED);

    private final FestivalSettlementClient festivals;
    private final ReservationServiceClient reservations;
    private final PaymentRepository payments;
    private final FestivalRefundBatchRepository batches;
    private final FestivalRefundItemRepository items;
    private final PaymentCancellationService cancellations;

    @Scheduled(fixedDelayString = "${festival-refund.delay-ms:60000}")
    public void run() {
        for (var candidate : festivals.refundCandidates()) {
            try {
                process(candidate);
            } catch (RuntimeException e) {
                log.warn("행사 전액 환불 재시도 필요: festival={}", candidate.festivalId(), e);
            }
        }
    }

    public void process(FestivalSettlementClient.RefundCandidate candidate) {
        var batch = batches.findByFestivalId(candidate.festivalId())
                .orElseGet(() -> batches.save(
                        new FestivalRefundBatch(candidate.festivalId(), candidate.initiatedBy(), candidate.reason())));
        var rows = reservations.settlementContext(candidate.festivalId());
        var paymentRows = payments.findByReservationIdIn(rows.stream().map(r -> r.reservationId()).toList());
        var previous = items.findByBatchId(batch.getId());
        for (var p : paymentRows) {
            if (!REFUNDABLE_STATUSES.contains(p.getStatus())) {
                continue;
            }
            //예매 확정이 거절된 결제는 예매에 반영된 적이 없어 PaymentCompensationScheduler가 따로 전액 환불한다.
            if (p.isReservationRejected()) {
                continue;
            }
            if (previous.stream().noneMatch(i -> i.getPaymentId().equals(p.getPaymentId()))) {
                items.save(
                        new FestivalRefundItem(batch.getId(), p.getPaymentId(), p.getReservationId())
                );
            }
        }
        var work = items.findByBatchId(batch.getId());
        for (var item : work) {
            if (item.getStatus() == FestivalRefundItemStatus.SUCCEEDED) {
                continue;
            }
            try {
                boolean refunded = cancellations.organizerRefund(
                        item.getPaymentId(), item.getIdempotencyKey(), batch.getInitiatedBy(), batch.getReason());
                if (refunded) {
                    item.success();
                } else {
                    item.fail();
                }
            } catch (RuntimeException e) {
                item.fail();
                log.warn("환불 항목 재시도: item={}", item.getId(), e);
            }
            items.save(item);
        }
        int succeeded = (int) work.stream()
                .filter(i -> i.getStatus() == FestivalRefundItemStatus.SUCCEEDED)
                .count();
        batch.progress(work.size(), succeeded, work.size() - succeeded);
        batches.save(batch);
        var latest = reservations.settlementContext(candidate.festivalId());
        boolean incomplete = latest.stream().anyMatch(r -> !Set.of("CANCELLED", "REFUNDED").contains(r.status()));
        // PG 취소 성공뿐 아니라 모든 예약의 환불 반영까지 끝나야 행사를 취소 완료로 바꾼다.
        if (succeeded == work.size() && !incomplete) {
            festivals.completeCancellation(candidate.festivalId());
            batch.complete();
            batches.save(batch);
        }
    }
}
