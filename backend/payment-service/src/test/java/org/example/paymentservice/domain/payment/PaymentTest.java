package org.example.paymentservice.domain.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private Payment payment(PaymentStatus status) {
        return Payment.builder()
                .paymentId("BE24-T05-1")
                .reservationId(1L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(status)
                .build();
    }

    @Test
    void READY에서_PAID로_전이할_수_있다() {
        Payment payment = payment(PaymentStatus.READY);

        payment.transitionTo(PaymentStatus.PAID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void 같은_paymentId가_FAILED였다가_재시도로_PAID가_될_수_있다() {
        Payment payment = payment(PaymentStatus.FAILED);

        payment.transitionTo(PaymentStatus.PAID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void CANCELLED에서_다른_상태로_역행할_수_없다() {
        Payment payment = payment(PaymentStatus.CANCELLED);

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.PAID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void PAID에서_PENDING으로_되돌아갈_수_없다() {
        Payment payment = payment(PaymentStatus.PAID);

        assertThatThrownBy(() -> payment.transitionTo(PaymentStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 티켓금액과_수수료를_합쳐_총액을_계산한다() {
        Payment payment = Payment.builder()
                .paymentId("BE24-T05-2")
                .reservationId(1L)
                .ticketAmount(10_000L)
                .platformFee(500L)
                .currency("KRW")
                .status(PaymentStatus.READY)
                .build();

        assertThat(payment.totalAmount()).isEqualTo(10_500L);
    }
}
