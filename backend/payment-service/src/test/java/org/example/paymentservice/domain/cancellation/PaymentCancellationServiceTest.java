package org.example.paymentservice.domain.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.dto.PaymentCancellationResponse;
import org.example.paymentservice.domain.payment.Payment;
import org.example.paymentservice.domain.payment.PaymentErrorCode;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.RefundReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationRefundQuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentCancellationServiceTest {

    private static final String PAYMENT_ID = "BE24-T05-1";
    private static final String IDEMPOTENCY_KEY = "refund-key-1";
    private static final Long USER_ID = 10L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CancellationRepository cancellationRepository;
    @Mock
    private PortOnePaymentClient portOnePaymentClient;
    @Mock
    private ReservationServiceClient reservationServiceClient;

    private PaymentCancellationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentCancellationService(
                paymentRepository, cancellationRepository, portOnePaymentClient, reservationServiceClient);

        when(cancellationRepository.save(any(Cancellation.class))).thenAnswer(i -> i.getArgument(0));
        when(cancellationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(cancellationRepository.findByCancellationId(anyString())).thenReturn(Optional.empty());
        when(cancellationRepository.findByPayment(any())).thenReturn(List.of());
    }

    private Payment paidPayment() {
        return Payment.builder()
                .paymentId(PAYMENT_ID)
                .reservationId(1L)
                .userId(USER_ID)
                .ticketAmount(30_000L)
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.PAID)
                .build();
    }

    private ReservationRefundQuoteResponse quote(boolean refundable, String rejectReason, int quantity, long refundAmount) {
        //구매 수량은 3장, 그중 quantity장을 환불하는 견적
        return new ReservationRefundQuoteResponse(1L, USER_ID, PAYMENT_ID, quantity, 0, 3,
                refundable, rejectReason, 10, 30_000L, 3_000L, refundAmount);
    }

    private PortOnePaymentResponse remotePayment(long cancelledTotal, List<PortOnePaymentResponse.Cancellation> cancellations) {
        return new PortOnePaymentResponse(PAYMENT_ID, "PAID", "TX-1", "store-test", null, null,
                new PortOnePaymentResponse.Amount(30_000L, 0, 0, 0, 0, 30_000L, cancelledTotal, 0),
                "KRW", "테스트", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pg-1",
                cancellations);
    }

    @Test
    @DisplayName("부분 환불이 성공하면 결제가 PARTIAL_CANCELLED가 되고 그 장수만큼 예매에 환불을 통보한다")
    void 부분_환불_성공() {
        Payment payment = paidPayment();
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(reservationServiceClient.getRefundQuote(1L, 1)).thenReturn(quote(true, null, 1, 9_000L));
        when(portOnePaymentClient.cancelPayment(eq(PAYMENT_ID), eq(9_000L), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new PortOneCancelResponse(new PortOnePaymentResponse.Cancellation(
                        "cancel-1", "SUCCEEDED", "사용자 요청", 9_000L, Instant.now(), Instant.now())));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(remotePayment(9_000L, List.of(
                new PortOnePaymentResponse.Cancellation("cancel-1", "SUCCEEDED", "사용자 요청", 9_000L, Instant.now(), Instant.now()))));

        PaymentCancellationResponse response =
                service.cancel(USER_ID, PAYMENT_ID, 1, "사용자 요청", IDEMPOTENCY_KEY);

        assertThat(response.cancelledAmount()).isEqualTo(9_000L);
        assertThat(response.cancelledQuantity()).isEqualTo(1);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELLED);
        verify(reservationServiceClient).refundReservation(1L, new RefundReservationRequest(PAYMENT_ID, 1));
    }

    @Test
    @DisplayName("전액이 취소되면 결제가 CANCELLED가 된다")
    void 전액_환불이면_CANCELLED() {
        Payment payment = paidPayment();
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(reservationServiceClient.getRefundQuote(1L, null)).thenReturn(quote(true, null, 3, 30_000L));
        when(portOnePaymentClient.cancelPayment(eq(PAYMENT_ID), eq(30_000L), any(), anyString()))
                .thenReturn(new PortOneCancelResponse(new PortOnePaymentResponse.Cancellation(
                        "cancel-2", "SUCCEEDED", null, 30_000L, Instant.now(), Instant.now())));
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(remotePayment(30_000L, List.of(
                new PortOnePaymentResponse.Cancellation("cancel-2", "SUCCEEDED", null, 30_000L, Instant.now(), Instant.now()))));

        service.cancel(USER_ID, PAYMENT_ID, null, null, IDEMPOTENCY_KEY);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("같은 멱등 키로 다시 요청하면 PortOne을 다시 호출하지 않는다")
    void 같은_멱등키_재요청은_중복_취소하지_않는다() {
        Payment payment = paidPayment();
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cancellationRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(
                Cancellation.builder()
                        .payment(payment)
                        .cancellationId("cancel-1")
                        .idempotencyKey(IDEMPOTENCY_KEY)
                        .status(CancellationStatus.SUCCEEDED)
                        .source(CancellationSource.API_REQUEST)
                        .amount(9_000L)
                        .quantity(1)
                        .build()));

        PaymentCancellationResponse response =
                service.cancel(USER_ID, PAYMENT_ID, 1, "사용자 요청", IDEMPOTENCY_KEY);

        assertThat(response.cancellationId()).isEqualTo("cancel-1");
        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyLong(), any(), anyString());
        verify(reservationServiceClient, never()).refundReservation(anyLong(), any());
    }

    @Test
    @DisplayName("환불 창이 닫힌 예매는 PortOne 취소를 시도하지 않고 사유를 구분해 거절한다")
    void 환불_불가_구간은_거절된다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(reservationServiceClient.getRefundQuote(anyLong(), any()))
                .thenReturn(quote(false, "REFUND_WINDOW_CLOSED", 1, 0L));

        assertThatThrownBy(() -> service.cancel(USER_ID, PAYMENT_ID, 1, null, IDEMPOTENCY_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.REFUND_WINDOW_CLOSED));

        verify(portOnePaymentClient, never()).cancelPayment(anyString(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("이미 입장한 예매는 환불되지 않는다")
    void 이미_입장한_예매는_환불_거절() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(reservationServiceClient.getRefundQuote(anyLong(), any()))
                .thenReturn(quote(false, "ALREADY_CHECKED_IN_NOT_REFUNDABLE", 1, 0L));

        assertThatThrownBy(() -> service.cancel(USER_ID, PAYMENT_ID, 1, null, IDEMPOTENCY_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ALREADY_CHECKED_IN_NOT_REFUNDABLE));
    }

    @Test
    @DisplayName("본인 결제가 아니면 환불할 수 없다")
    void 타인_결제는_환불_불가() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));

        assertThatThrownBy(() -> service.cancel(999L, PAYMENT_ID, 1, null, IDEMPOTENCY_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.FORBIDDEN_PAYMENT_OWNER));
    }

    @Test
    @DisplayName("PortOne 취소 호출이 실패하면 예매 재고를 되돌리지 않는다")
    void 취소_호출_실패시_예매는_건드리지_않는다() {
        when(paymentRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(reservationServiceClient.getRefundQuote(1L, 1)).thenReturn(quote(true, null, 1, 9_000L));
        when(portOnePaymentClient.cancelPayment(anyString(), anyLong(), any(), anyString()))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> service.cancel(USER_ID, PAYMENT_ID, 1, null, IDEMPOTENCY_KEY))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.REFUND_FAILED));

        verify(reservationServiceClient, never()).refundReservation(anyLong(), any());
    }

    @Test
    @DisplayName("외부(PortOne 대시보드)에서 발생한 취소도 재조회 대사로 반영된다")
    void 외부_취소도_대사로_반영된다() {
        Payment payment = paidPayment();
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(remotePayment(30_000L, List.of(
                new PortOnePaymentResponse.Cancellation("external-1", "SUCCEEDED", "관리자 취소", 30_000L, Instant.now(), Instant.now()))));

        service.reconcile(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 전액 취소된 결제는 늦게 도착한 부분취소 이벤트로 되돌아가지 않는다")
    void 순서_역전은_상태를_되돌리지_않는다() {
        Payment payment = paidPayment();
        payment.transitionTo(PaymentStatus.CANCELLED);
        when(portOnePaymentClient.getPayment(PAYMENT_ID)).thenReturn(remotePayment(9_000L, List.of(
                new PortOnePaymentResponse.Cancellation("cancel-1", "PENDING", null, 9_000L, Instant.now(), null))));

        service.reconcile(payment);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }
}
