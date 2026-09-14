package org.example.paymentservice.domain.cancellation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.example.paymentservice.domain.settlement.FestivalSettlementClient;
import org.example.paymentservice.domain.payment.*;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import java.util.*;

@Component @RequiredArgsConstructor @Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "festival-refund.scheduling-enabled", matchIfMissing = true)
public class FestivalRefundScheduler {
    private final FestivalSettlementClient festivals;
    private final ReservationServiceClient reservations;
    private final PaymentRepository payments;
    private final FestivalRefundBatchRepository batches;
    private final FestivalRefundItemRepository items;
    private final PaymentCancellationService cancellations;
    @Scheduled(fixedDelayString = "${festival-refund.delay-ms:60000}")
    public void run() {
        for (var candidate : festivals.refundCandidates()) {
            try { process(candidate); }
            catch (RuntimeException e) { log.warn("행사 전액 환불 재시도 필요: festival={}", candidate.festivalId(), e); }
        }
    }
    public void process(FestivalSettlementClient.RefundCandidate candidate) {
        var batch = batches.findByFestivalId(candidate.festivalId()).orElseGet(() -> batches.save(
                new FestivalRefundBatch(candidate.festivalId(), candidate.initiatedBy(), candidate.reason())));
        var rows = reservations.settlementContext(candidate.festivalId());
        var paymentRows = payments.findByReservationIdIn(rows.stream().map(r -> r.reservationId()).toList());
        var previous = items.findByBatchId(batch.getId());
        for (var p : paymentRows) {
            if (!Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED).contains(p.getStatus())) continue;
            if (previous.stream().noneMatch(i -> i.getPaymentId().equals(p.getPaymentId())))
                items.save(new FestivalRefundItem(batch.getId(), p.getPaymentId(), p.getReservationId()));
        }
        var work = items.findByBatchId(batch.getId());
        for (var item : work) {
            if ("SUCCEEDED".equals(item.getStatus())) continue;
            try {
                if (cancellations.organizerRefund(item.getPaymentId(), item.getIdempotencyKey(), batch.getInitiatedBy(), batch.getReason())) item.success();
                else item.fail();
            } catch (RuntimeException e) { item.fail(); log.warn("환불 항목 재시도: item={}", item.getId(), e); }
            items.save(item);
        }
        int succeeded = (int) work.stream().filter(i -> "SUCCEEDED".equals(i.getStatus())).count();
        batch.progress(work.size(), succeeded, work.size() - succeeded); batches.save(batch);
        var latest = reservations.settlementContext(candidate.festivalId());
        boolean incomplete = latest.stream().anyMatch(r -> !Set.of("CANCELLED", "REFUNDED").contains(r.status()));
        if (succeeded == work.size() && !incomplete) {
            festivals.completeCancellation(candidate.festivalId()); batch.complete(); batches.save(batch);
        }
    }
}
