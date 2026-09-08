package org.example.paymentservice.domain.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private Payment savedPayment(String paymentId) {
        return paymentRepository.save(Payment.builder()
                .paymentId(paymentId)
                .reservationId(1L)
                .userId(1L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.READY)
                .build());
    }

    @Test
    void payment_id는_중복될_수_없다() {
        savedPayment("BE24-T05-1");

        assertThatThrownBy(() -> {
            paymentRepository.saveAndFlush(Payment.builder()
                    .paymentId("BE24-T05-1")
                    .reservationId(2L)
                    .userId(1L)
                    .ticketAmount(5_000L)
                    .platformFee(0L)
                    .currency("KRW")
                    .status(PaymentStatus.READY)
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentId로_조회할_수_있다() {
        savedPayment("BE24-T05-2");

        assertThat(paymentRepository.findByPaymentId("BE24-T05-2")).isPresent();
        assertThat(paymentRepository.findByPaymentId("존재하지-않음")).isEmpty();
    }

    @Test
    void 같은_Payment에_여러_PaymentTransaction을_기록할_수_있다() {
        Payment payment = savedPayment("BE24-T05-3");

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .transactionId("TX-1")
                .status(PaymentStatus.FAILED)
                .amount(10_000L)
                .build());
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .transactionId("TX-2")
                .status(PaymentStatus.PAID)
                .amount(10_000L)
                .approvedAt(LocalDateTime.now())
                .build());

        List<PaymentTransaction> all = paymentTransactionRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(PaymentTransaction::getStatus)
                .containsExactlyInAnyOrder(PaymentStatus.FAILED, PaymentStatus.PAID);
    }

    @Test
    void transaction_id는_중복될_수_없다() {
        Payment payment = savedPayment("BE24-T05-4");
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .transactionId("TX-DUP")
                .status(PaymentStatus.PAID)
                .amount(10_000L)
                .build());

        assertThatThrownBy(() -> paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder()
                .payment(payment)
                .transactionId("TX-DUP")
                .status(PaymentStatus.PAID)
                .amount(10_000L)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
