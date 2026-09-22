package org.example.paymentservice.domain.payment;

import org.example.paymentservice.domain.cancellation.Cancellation;
import org.example.paymentservice.domain.cancellation.CancellationRepository;
import org.example.paymentservice.domain.cancellation.CancellationStatus;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDepositRemoteTest {

    private static final String PAYMENT_ID = "BE24-T05-1";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CancellationRepository cancellationRepository;

    private DemoDepositRemote demoDepositRemote;

    @BeforeEach
    void setUp() {
        demoDepositRemote = new DemoDepositRemote(paymentRepository, cancellationRepository);
        ReflectionTestUtils.setField(demoDepositRemote, "storeId", "store-test");
        ReflectionTestUtils.setField(demoDepositRemote, "channelKeyPayment", "channel-key-test");
    }

    private Payment payment(boolean demoDeposited) {
        Payment payment = Payment.builder()
                .paymentId(PAYMENT_ID)
                .reservationId(1L)
                .userId(10L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED)
                .build();
        payment.markVirtualAccountIssued();
        if (demoDeposited) {
            payment.markDemoDeposited();
        }
        return payment;
    }

    private Cancellation demoCancellation(Payment payment, long amount, CancellationStatus status) {
        return Cancellation.builder()
                .payment(payment)
                .cancellationId("demo-key-1")
                .idempotencyKey("key-1")
                .status(status)
                .amount(amount)
                .quantity(1)
                .cancelledAt(Instant.now())
                .build();
    }

    @Test
    void 데모_입금이_아니면_비어_있어서_실제_PortOne을_조회한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(false)));

        assertThat(demoDepositRemote.remoteView(PAYMENT_ID)).isEmpty();
        assertThat(demoDepositRemote.cancel(PAYMENT_ID, null, "key-1")).isEmpty();
    }

    @Test
    void 데모_입금_결제는_검증이_통과하는_PAID_응답으로_보인다() {
        Payment payment = payment(true);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cancellationRepository.findByPayment(payment)).thenReturn(List.of());

        PortOnePaymentResponse remote = demoDepositRemote.remoteView(PAYMENT_ID).orElseThrow();

        //PaymentService.validateRemotePayment와 SettlementService.gather가 대조하는 필드가 전부 맞아야 한다.
        assertThat(remote.status()).isEqualTo("PAID");
        assertThat(remote.id()).isEqualTo(PAYMENT_ID);
        assertThat(remote.storeId()).isEqualTo("store-test");
        assertThat(remote.channel().key()).isEqualTo("channel-key-test");
        assertThat(remote.channel().type()).isEqualTo("TEST");
        assertThat(remote.currency()).isEqualTo("KRW");
        assertThat(remote.amount().total()).isEqualTo(10_000L);
        assertThat(remote.amount().cancelled()).isZero();
        assertThat(remote.method().type()).isEqualTo("VIRTUAL_ACCOUNT");
        assertThat(remote.paidAt()).isEqualTo(payment.getDemoDepositedAt());
        assertThat(remote.cancellations()).isEmpty();
    }

    @Test
    void 데모_취소는_PortOne_없이_성공_취소로_응답하고_재조회에_누적된다() {
        Payment payment = payment(true);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cancellationRepository.findByPayment(payment)).thenReturn(List.of());

        PortOneCancelResponse cancelled = demoDepositRemote.cancel(PAYMENT_ID, 4_000L, "key-1").orElseThrow();

        assertThat(cancelled.cancellation().id()).isEqualTo("demo-key-1");
        assertThat(cancelled.cancellation().status()).isEqualTo("SUCCEEDED");
        assertThat(cancelled.cancellation().totalAmount()).isEqualTo(4_000L);

        //호출자가 취소 행을 저장한 뒤 재조회하면 취소 목록·누적 취소액·상태가 함께 바뀐다.
        when(cancellationRepository.findByPayment(payment))
                .thenReturn(List.of(demoCancellation(payment, 4_000L, CancellationStatus.SUCCEEDED)));

        PortOnePaymentResponse remote = demoDepositRemote.remoteView(PAYMENT_ID).orElseThrow();

        assertThat(remote.status()).isEqualTo("PARTIAL_CANCELLED");
        assertThat(remote.amount().cancelled()).isEqualTo(4_000L);
        assertThat(remote.cancellations()).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo("demo-key-1");
                    assertThat(c.status()).isEqualTo("SUCCEEDED");
                    assertThat(c.totalAmount()).isEqualTo(4_000L);
                });
    }

    @Test
    void 아직_성공하지_않은_취소_행은_원격_취소_목록에_넣지_않는다() {
        Payment payment = payment(true);
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cancellationRepository.findByPayment(payment))
                .thenReturn(List.of(demoCancellation(payment, 10_000L, CancellationStatus.REQUESTED)));

        PortOnePaymentResponse remote = demoDepositRemote.remoteView(PAYMENT_ID).orElseThrow();

        assertThat(remote.status()).isEqualTo("PAID");
        assertThat(remote.cancellations()).isEmpty();
        //전액 취소 금액을 생략하면 남은 금액 전부가 취소 대상이다.
        assertThat(demoDepositRemote.cancel(PAYMENT_ID, null, "key-2").orElseThrow().cancellation().totalAmount())
                .isEqualTo(10_000L);
    }
}
