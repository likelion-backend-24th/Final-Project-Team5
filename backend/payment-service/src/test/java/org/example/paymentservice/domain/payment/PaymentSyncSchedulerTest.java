package org.example.paymentservice.domain.payment;

import org.example.paymentservice.domain.cancellation.CancellationRepository;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 웹훅이 오지 않은 결제를 배치가 PortOne 재조회로 마무리하는지 H2와 실제 PaymentService로 확인한다.
 * PortOne·Reservation-Service는 Client 계층만 대역으로 바꾸고, 스케줄 실행이 끼어들지 않도록 배치는 직접 만들어 호출한다.
 */
@SpringBootTest
class PaymentSyncSchedulerTest {

    private static final long RESERVATION_ID = 1L;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private CancellationRepository cancellationRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PortOnePaymentClient portOnePaymentClient;

    @MockitoBean
    private ReservationServiceClient reservationServiceClient;

    private PaymentSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(portOnePaymentClient, reservationServiceClient);
        scheduler = new PaymentSyncScheduler(paymentRepository, paymentService, 10, 24);
    }

    @AfterEach
    void cleanUp() {
        cancellationRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private void saved(String paymentId, PaymentStatus status, Instant paidAt, Instant confirmedAt) {
        paymentRepository.save(Payment.builder()
                .paymentId(paymentId)
                .reservationId(RESERVATION_ID)
                .userId(10L)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(status)
                .paidAt(paidAt)
                .reservationConfirmedAt(confirmedAt)
                .build());
    }

    //@CreationTimestamp가 생성 시각을 덮어쓰므로 저장 뒤에 원하는 시각으로 옮긴다.
    private void createdAgo(String paymentId, Duration ago) {
        jdbcTemplate.update("UPDATE payments SET created_at = ? WHERE payment_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minus(ago)), paymentId);
    }

    private PortOnePaymentResponse paid(String paymentId) {
        return new PortOnePaymentResponse(paymentId, "PAID", "TX-" + paymentId, "store-test",
                new PortOnePaymentResponse.Channel("channel-id-1", "channel-key-test", "TEST", "토스페이먼츠_일반", "TOSSPAYMENTS"),
                new PortOnePaymentResponse.Method("CARD", null, null, null, null, null, null),
                new PortOnePaymentResponse.Amount(10_000L, 0, 0, 10_000L, 0, 10_000L, 0, 0),
                "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-1", null);
    }

    @Test
    void 웹훅이_오지_않은_승인_결제를_재조회해_예매를_확정한다() {
        saved("missed-webhook", PaymentStatus.READY, null, null);
        createdAgo("missed-webhook", Duration.ofMinutes(15));
        when(portOnePaymentClient.getPayment("missed-webhook")).thenReturn(paid("missed-webhook"));

        scheduler.run();

        Payment payment = paymentRepository.findByPaymentId("missed-webhook").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReservationConfirmedAt()).isNotNull();
        verify(reservationServiceClient).confirmReservation(eq(RESERVATION_ID), any());
    }

    @Test
    void 결제창이_열려_있을_수_있는_최근_결제와_조회_기간이_지난_결제는_건드리지_않는다() {
        saved("just-opened", PaymentStatus.READY, null, null);
        saved("too-old", PaymentStatus.READY, null, null);
        createdAgo("too-old", Duration.ofDays(2));

        scheduler.run();

        verify(portOnePaymentClient, never()).getPayment(anyString());
        assertThat(paymentRepository.findByPaymentId("just-opened").orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 승인됐지만_예매_확정_응답을_받지_못한_결제는_확정을_다시_요청한다() {
        saved("confirm-timeout", PaymentStatus.PAID, Instant.now().minus(Duration.ofMinutes(20)), null);
        createdAgo("confirm-timeout", Duration.ofMinutes(20));
        saved("already-confirmed", PaymentStatus.PAID, Instant.now().minus(Duration.ofMinutes(20)), Instant.now());
        createdAgo("already-confirmed", Duration.ofMinutes(20));

        scheduler.run();

        assertThat(paymentRepository.findByPaymentId("confirm-timeout").orElseThrow().getReservationConfirmedAt()).isNotNull();
        verify(reservationServiceClient).confirmReservation(eq(RESERVATION_ID),
                org.mockito.ArgumentMatchers.argThat(request -> "confirm-timeout".equals(request.paymentId())));
        verify(reservationServiceClient, never()).confirmReservation(eq(RESERVATION_ID),
                org.mockito.ArgumentMatchers.argThat(request -> "already-confirmed".equals(request.paymentId())));
        verify(portOnePaymentClient, never()).getPayment(anyString());
    }

    @Test
    void PortOne이_아직_모르는_결제가_있어도_나머지_결제는_계속_처리한다() {
        saved("not-submitted", PaymentStatus.READY, null, null);
        createdAgo("not-submitted", Duration.ofMinutes(30));
        saved("paid-later", PaymentStatus.READY, null, null);
        createdAgo("paid-later", Duration.ofMinutes(30));
        when(portOnePaymentClient.getPayment("not-submitted")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));
        when(portOnePaymentClient.getPayment("paid-later")).thenReturn(paid("paid-later"));

        scheduler.run();

        assertThat(paymentRepository.findByPaymentId("not-submitted").orElseThrow().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(paymentRepository.findByPaymentId("paid-later").orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
