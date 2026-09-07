package org.example.paymentservice.domain.payment;

import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.dto.PaymentCompleteResponse;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ExtendReservationHoldRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private ReservationServiceClient reservationServiceClient;

    @Mock
    private PortOnePaymentClient portOnePaymentClient;

    private PaymentService paymentService;

    private static final PaymentPrepareRequest PREPARE_REQUEST = new PaymentPrepareRequest(1L);
    private static final String PAYMENT_ID = "BE24-T05-1";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, paymentTransactionRepository, reservationServiceClient, portOnePaymentClient);
        ReflectionTestUtils.setField(paymentService, "paymentIdPrefix", "BE24-T05-");
        ReflectionTestUtils.setField(paymentService, "storeId", "store-test");
        ReflectionTestUtils.setField(paymentService, "channelKeyPayment", "channel-key-test");
    }

    private ReservationForPaymentResponse reservation(Long userId, String status) {
        return new ReservationForPaymentResponse(1L, userId, status, 10_000L, 2L, 1, Instant.now());
    }

    private Payment payment(Long userId, PaymentStatus status) {
        return Payment.builder()
                .paymentId(PAYMENT_ID)
                .reservationId(1L)
                .userId(userId)
                .ticketAmount(10_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(status)
                .build();
    }

    private PortOnePaymentResponse.Channel channel() {
        return new PortOnePaymentResponse.Channel("channel-id-1", "channel-key-test", "TEST", "토스페이먼츠_일반", "TOSSPAYMENTS");
    }

    private PortOnePaymentResponse.Amount amount(long total) {
        return new PortOnePaymentResponse.Amount(total, 0, 0, total, 0, total, 0, 0);
    }

    private PortOnePaymentResponse paidResponse(long total) {
        return new PortOnePaymentResponse(PAYMENT_ID, "PAID", "TX-1", "store-test", channel(),
                new PortOnePaymentResponse.Method("PaymentMethodCard", null, null, null, null, null, null),
                amount(total), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-1");
    }

    private PortOnePaymentResponse failedResponse() {
        return new PortOnePaymentResponse(PAYMENT_ID, "FAILED", "TX-2", "store-test", channel(), null,
                amount(10_000L), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), null, Instant.now(),
                new PortOnePaymentResponse.Failure("사용자가 결제를 취소하였습니다", "PAY_PROCESS_CANCELED", "사용자가 결제를 취소하였습니다"), "pgtx-2");
    }

    private PortOnePaymentResponse virtualAccountIssuedResponse(Instant expiredAt) {
        return new PortOnePaymentResponse(PAYMENT_ID, "VIRTUAL_ACCOUNT_ISSUED", "TX-3", "store-test", channel(),
                new PortOnePaymentResponse.Method("PaymentMethodVirtualAccount", "KOOKMIN", "X590901", "NORMAL", "조민규", expiredAt, Instant.now()),
                amount(10_000L), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), null, null, null, "pgtx-3");
    }

    // ===== prepare =====

    @Test
    void 본인의_Pending_예매는_결제_준비에_성공한다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(10L, "PENDING"));

        PaymentPrepareResponse response = paymentService.prepare(10L, PREPARE_REQUEST);

        assertThat(response.storeId()).isEqualTo("store-test");
        assertThat(response.channelKey()).isEqualTo("channel-key-test");
        assertThat(response.totalAmount()).isEqualTo(10_000L);
        assertThat(response.paymentId()).startsWith("BE24-T05-");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void 타인의_예매는_결제_준비가_거부된다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(99L, "PENDING"));

        assertThatThrownBy(() -> paymentService.prepare(10L, PREPARE_REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.FORBIDDEN_RESERVATION_OWNER));
    }

    @Test
    void PENDING이_아닌_예매는_결제_준비가_거부된다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(10L, "CONFIRMED"));

        assertThatThrownBy(() -> paymentService.prepare(10L, PREPARE_REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_NOT_PAYABLE));
    }

    @Test
    void 존재하지_않는_예매는_404로_변환된다() {
        when(reservationServiceClient.getReservation(1L))
                .thenThrow(HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> paymentService.prepare(10L, PREPARE_REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_NOT_FOUND));
    }

    @Test
    void 예매_서비스_장애는_503으로_변환된다() {
        when(reservationServiceClient.getReservation(1L))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> paymentService.prepare(10L, PREPARE_REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE));
    }

    // ===== syncPayment (웹훅 경로 — 소유권 검사 없음) =====

    @Test
    void syncPayment은_소유권_확인_없이_PAID를_확정한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidResponse(10_000L));

        PaymentCompleteResponse response = paymentService.syncPayment(PAYMENT_ID);

        assertThat(response.status()).isEqualTo("PAID");
        verify(reservationServiceClient).confirmReservation(eq(1L), any(ConfirmReservationRequest.class));
    }

    @Test
    void syncPayment은_존재하지_않는_결제면_404다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.syncPayment(PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // ===== complete =====

    @Test
    void PAID_결제는_확정되고_예매_확정을_호출한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(paymentTransactionRepository.findByTransactionId("TX-1")).thenReturn(Optional.empty());
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidResponse(10_000L));

        PaymentCompleteResponse response = paymentService.complete(10L, PAYMENT_ID);

        assertThat(response.status()).isEqualTo("PAID");
        verify(reservationServiceClient).confirmReservation(eq(1L), any(ConfirmReservationRequest.class));
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void 이미_PAID인_결제는_재확인_없이_멱등하게_반환한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.PAID)));

        PaymentCompleteResponse response = paymentService.complete(10L, PAYMENT_ID);

        assertThat(response.status()).isEqualTo("PAID");
        verify(portOnePaymentClient, never()).getPayment(any());
        verify(reservationServiceClient, never()).confirmReservation(anyLong(), any());
    }

    @Test
    void 타인의_결제_완료_요청은_거부된다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));

        assertThatThrownBy(() -> paymentService.complete(99L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.FORBIDDEN_PAYMENT_OWNER));
    }

    @Test
    void 존재하지_않는_결제_완료_요청은_404다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.complete(10L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Test
    void 금액이_변조되면_확정을_거부한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidResponse(1_000L));

        assertThatThrownBy(() -> paymentService.complete(10L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED));
        verify(reservationServiceClient, never()).confirmReservation(anyLong(), any());
    }

    @Test
    void 채널키가_다르면_확정을_거부한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        PortOnePaymentResponse wrongChannel = new PortOnePaymentResponse(PAYMENT_ID, "PAID", "TX-9", "store-test",
                new PortOnePaymentResponse.Channel("channel-id-9", "다른-채널-키", "TEST", "토스페이먼츠_일반", "TOSSPAYMENTS"),
                null, amount(10_000L), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-9");
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(wrongChannel);

        assertThatThrownBy(() -> paymentService.complete(10L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED));
    }

    @Test
    void FAILED_결제는_예매_취소를_호출한다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(failedResponse());

        PaymentCompleteResponse response = paymentService.complete(10L, PAYMENT_ID);

        assertThat(response.status()).isEqualTo("FAILED");
        verify(reservationServiceClient).cancelReservation(eq(1L),
                argThatReasonIsPaymentFailed());
    }

    @Test
    void 가상계좌_발급이면_홀드_연장을_요청한다() {
        Instant expiredAt = Instant.parse("2026-09-08T07:32:37Z");
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(virtualAccountIssuedResponse(expiredAt));

        PaymentCompleteResponse response = paymentService.complete(10L, PAYMENT_ID);

        assertThat(response.status()).isEqualTo("VIRTUAL_ACCOUNT_ISSUED");
        verify(reservationServiceClient).extendReservationHold(1L, new ExtendReservationHoldRequest(expiredAt));
        verify(reservationServiceClient, never()).confirmReservation(anyLong(), any());
    }

    @Test
    void 이미_만료된_예매의_확정_실패는_409로_변환된다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidResponse(10_000L));
        org.mockito.Mockito.doThrow(HttpClientErrorException.Conflict.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(reservationServiceClient).confirmReservation(anyLong(), any());

        assertThatThrownBy(() -> paymentService.complete(10L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_ALREADY_FINALIZED));
    }

    @Test
    void 알_수_없는_상태는_거부된다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        PortOnePaymentResponse unknown = new PortOnePaymentResponse(PAYMENT_ID, "SOME_NEW_STATUS", null, "store-test",
                channel(), null, amount(10_000L), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), null, null, null, null);
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(unknown);

        assertThatThrownBy(() -> paymentService.complete(10L, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.UNEXPECTED_PAYMENT_STATUS));
    }

    @Test
    void 이미_기록된_transactionId는_다시_저장하지_않는다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment(10L, PaymentStatus.READY)));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(paidResponse(10_000L));
        when(paymentTransactionRepository.findByTransactionId("TX-1"))
                .thenReturn(Optional.of(PaymentTransaction.builder().transactionId("TX-1").status(PaymentStatus.PAID).amount(10_000L).build()));

        paymentService.complete(10L, PAYMENT_ID);

        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }

    private org.example.paymentservice.infrastructure.reservation.dto.CancelReservationRequest argThatReasonIsPaymentFailed() {
        return org.mockito.ArgumentMatchers.argThat(req -> req != null && "PAYMENT_FAILED".equals(req.reasonCode()));
    }
}
