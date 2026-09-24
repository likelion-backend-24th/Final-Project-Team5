package org.example.paymentservice.domain.cancellation;
import org.example.paymentservice.domain.payment.*;
import org.example.paymentservice.domain.settlement.FestivalSettlementClient;
import org.example.paymentservice.infrastructure.reservation.*;
import org.example.paymentservice.infrastructure.reservation.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class FestivalRefundSchedulerTest {
    @Test void failedItemRetriesWithSameKeyAndFestivalCompletesOnlyAfterReservationRefund() {
        var festivals = mock(FestivalSettlementClient.class); var reservations = mock(ReservationServiceClient.class);
        var payments = mock(PaymentRepository.class); var batches = mock(FestivalRefundBatchRepository.class);
        var items = mock(FestivalRefundItemRepository.class); var cancellations = mock(PaymentCancellationService.class);
        var worker = new FestivalRefundScheduler(festivals, reservations, payments, batches, items, cancellations);
        var batch = new FestivalRefundBatch(42L, 10L, "행사 취소"); ReflectionTestUtils.setField(batch, "id", 1L);
        var item = new FestivalRefundItem(1L, "p1", 5L);
        when(batches.findByFestivalId(42L)).thenReturn(Optional.of(batch)); when(items.findByBatchId(1L)).thenReturn(List.of(item));
        when(payments.findByReservationIdIn(any())).thenReturn(List.of(Payment.builder().paymentId("p1").reservationId(5L).status(PaymentStatus.PAID).build()));
        var confirmed = new ReservationForPaymentResponse(5L, 20L, "CONFIRMED", 10000, 3L, 1, null, 42L, 10L, 10000L, 0, "p1");
        var refunded = new ReservationForPaymentResponse(5L, 20L, "REFUNDED", 10000, 3L, 1, null, 42L, 10L, 10000L, 1, "p1");
        when(reservations.settlementContext(42L)).thenReturn(List.of(confirmed), List.of(confirmed), List.of(confirmed), List.of(refunded));
        when(cancellations.organizerRefund("p1", item.getIdempotencyKey(), 10L, "행사 취소")).thenReturn(false, true);
        var candidate = new FestivalSettlementClient.RefundCandidate(42L, 10L, 10L, "행사 취소");
        worker.process(candidate); verify(festivals, never()).completeCancellation(anyLong());
        assertThat(item.getRetryCount()).isEqualTo(1);
        worker.process(candidate); verify(festivals).completeCancellation(42L);
        verify(cancellations, times(2)).organizerRefund("p1", item.getIdempotencyKey(), 10L, "행사 취소");
        assertThat(batch.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test void reservationRejectedPaymentIsLeftToCompensationRefund() {
        var festivals = mock(FestivalSettlementClient.class); var reservations = mock(ReservationServiceClient.class);
        var payments = mock(PaymentRepository.class); var batches = mock(FestivalRefundBatchRepository.class);
        var items = mock(FestivalRefundItemRepository.class); var cancellations = mock(PaymentCancellationService.class);
        var worker = new FestivalRefundScheduler(festivals, reservations, payments, batches, items, cancellations);
        var batch = new FestivalRefundBatch(42L, 10L, "행사 취소"); ReflectionTestUtils.setField(batch, "id", 1L);
        when(batches.findByFestivalId(42L)).thenReturn(Optional.of(batch)); when(items.findByBatchId(1L)).thenReturn(List.of());
        //같은 예매의 두 번째 결제는 예매 확정이 거절돼 보상 배치가 따로 환불한다 — 주최자 귀책 환불 항목으로 만들지 않는다.
        var rejected = Payment.builder().paymentId("p2").reservationId(5L).status(PaymentStatus.CANCELLED)
                .reservationRejectedAt(java.time.Instant.now()).build();
        when(payments.findByReservationIdIn(any())).thenReturn(List.of(rejected));
        var refunded = new ReservationForPaymentResponse(5L, 20L, "REFUNDED", 10000, 3L, 1, null, 42L, 10L, 10000L, 1, "p1");
        when(reservations.settlementContext(42L)).thenReturn(List.of(refunded));
        worker.process(new FestivalSettlementClient.RefundCandidate(42L, 10L, 10L, "행사 취소"));
        verify(items, never()).save(any());
        verify(cancellations, never()).organizerRefund(anyString(), anyString(), anyLong(), anyString());
    }
}
