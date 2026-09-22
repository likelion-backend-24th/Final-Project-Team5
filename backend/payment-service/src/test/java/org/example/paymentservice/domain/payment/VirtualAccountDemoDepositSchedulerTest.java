package org.example.paymentservice.domain.payment;

import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 배치 → PaymentService.demoDeposit → 실제 PortOnePaymentClient(오버레이) → 예매 확정까지 한 번에 돈다.
 * PortOne HTTP는 오버레이가 가로채므로 외부 호출 없이 끝나야 한다.
 */
@SpringBootTest(properties = "payment.demo-deposit.enabled=true")
class VirtualAccountDemoDepositSchedulerTest {

    private static final String ISSUED_LONG_AGO = "BE24-T05-old";
    private static final String ISSUED_JUST_NOW = "BE24-T05-new";

    @Autowired
    private VirtualAccountDemoDepositScheduler scheduler;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ReservationServiceClient reservationServiceClient;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private Payment issued(String paymentId, Instant issuedAt) {
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .reservationId(1L)
                .userId(10L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED)
                .build();
        ReflectionTestUtils.setField(payment, "virtualAccountIssuedAt", issuedAt);
        return paymentRepository.save(payment);
    }

    @Test
    void 발급_후_지연_시간이_지난_가상계좌만_입금_처리해_예매를_확정한다() {
        issued(ISSUED_LONG_AGO, Instant.now().minus(31, ChronoUnit.MINUTES));
        issued(ISSUED_JUST_NOW, Instant.now().minus(1, ChronoUnit.MINUTES));

        scheduler.run();

        Payment old = paymentRepository.findByPaymentId(ISSUED_LONG_AGO).orElseThrow();
        assertThat(old.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(old.isDemoDeposited()).isTrue();
        assertThat(old.getPayMethodCategory()).isEqualTo(PaymentMethodCategory.VIRTUAL_ACCOUNT);
        assertThat(old.getReservationConfirmedAt()).isNotNull();
        verify(reservationServiceClient).confirmReservation(eq(1L), any(ConfirmReservationRequest.class));

        Payment fresh = paymentRepository.findByPaymentId(ISSUED_JUST_NOW).orElseThrow();
        assertThat(fresh.getStatus()).isEqualTo(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);
        assertThat(fresh.isDemoDeposited()).isFalse();
    }

    @Test
    void 이미_처리된_결제는_다음_주기에_다시_건드리지_않는다() {
        issued(ISSUED_LONG_AGO, Instant.now().minus(31, ChronoUnit.MINUTES));

        scheduler.run();
        scheduler.run();

        verify(reservationServiceClient).confirmReservation(eq(1L), any(ConfirmReservationRequest.class));
        verify(reservationServiceClient, never()).extendReservationHold(any(), any());
    }
}
