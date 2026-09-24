package org.example.paymentservice.domain.cancellation;

import org.example.paymentservice.domain.payment.Payment;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 → 보상 대상 조회(실제 H2) → 자동 환불 호출까지 확인한다. PortOne 취소 자체는 PaymentCancellationServiceTest가 본다.
 * 스케줄 실행이 테스트 중간에 끼어들지 않도록 배치 빈은 켜지 않고 직접 만들어 호출한다.
 */
@SpringBootTest
class PaymentCompensationSchedulerTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentCancellationService paymentCancellationService;

    private PaymentCompensationScheduler scheduler;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        scheduler = new PaymentCompensationScheduler(paymentRepository, paymentCancellationService);
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
    }

    private void saved(String paymentId, PaymentStatus status, Instant rejectedAt) {
        paymentRepository.save(Payment.builder()
                .paymentId(paymentId)
                .reservationId(1L)
                .userId(10L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(status)
                .reservationRejectedAt(rejectedAt)
                .build());
    }

    @Test
    void 확정이_거절된_지_조금_지났고_아직_환불되지_않은_결제만_다시_환불한다() {
        Instant earlier = Instant.now().minus(Duration.ofMinutes(5));
        saved("rejected-paid", PaymentStatus.PAID, earlier);
        saved("rejected-partial", PaymentStatus.PARTIAL_CANCELLED, earlier);
        saved("rejected-refunded", PaymentStatus.CANCELLED, earlier);
        saved("rejected-just-now", PaymentStatus.PAID, Instant.now());
        saved("confirmed", PaymentStatus.PAID, null);

        scheduler.run();

        verify(paymentCancellationService).compensate(argThat(p -> "rejected-paid".equals(p.getPaymentId())));
        verify(paymentCancellationService).compensate(argThat(p -> "rejected-partial".equals(p.getPaymentId())));
        verify(paymentCancellationService, times(2)).compensate(any());
    }

    @Test
    void 한_건의_환불이_실패해도_나머지는_계속_시도한다() {
        Instant earlier = Instant.now().minus(Duration.ofMinutes(5));
        saved("rejected-1", PaymentStatus.PAID, earlier);
        saved("rejected-2", PaymentStatus.PAID, earlier);
        when(paymentCancellationService.compensate(any())).thenThrow(new IllegalStateException("PortOne down"));

        scheduler.run();

        verify(paymentCancellationService, times(2)).compensate(any());
    }
}
