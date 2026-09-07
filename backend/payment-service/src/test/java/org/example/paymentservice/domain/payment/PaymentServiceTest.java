package org.example.paymentservice.domain.payment;

import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ReservationServiceClient reservationServiceClient;

    private PaymentService paymentService;

    private static final PaymentPrepareRequest REQUEST = new PaymentPrepareRequest(1L);

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, reservationServiceClient);
        ReflectionTestUtils.setField(paymentService, "paymentIdPrefix", "BE24-T05-");
        ReflectionTestUtils.setField(paymentService, "storeId", "store-test");
        ReflectionTestUtils.setField(paymentService, "channelKeyPayment", "channel-key-test");
    }

    private ReservationForPaymentResponse reservation(Long userId, String status) {
        return new ReservationForPaymentResponse(1L, userId, status, 10_000L, 2L, 1, Instant.now());
    }

    @Test
    void 본인의_Pending_예매는_결제_준비에_성공한다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(10L, "PENDING"));

        PaymentPrepareResponse response = paymentService.prepare(10L, REQUEST);

        assertThat(response.storeId()).isEqualTo("store-test");
        assertThat(response.channelKey()).isEqualTo("channel-key-test");
        assertThat(response.totalAmount()).isEqualTo(10_000L);
        assertThat(response.paymentId()).startsWith("BE24-T05-");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void 타인의_예매는_결제_준비가_거부된다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(99L, "PENDING"));

        assertThatThrownBy(() -> paymentService.prepare(10L, REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.FORBIDDEN_RESERVATION_OWNER));
    }

    @Test
    void PENDING이_아닌_예매는_결제_준비가_거부된다() {
        when(reservationServiceClient.getReservation(1L)).thenReturn(reservation(10L, "CONFIRMED"));

        assertThatThrownBy(() -> paymentService.prepare(10L, REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_NOT_PAYABLE));
    }

    @Test
    void 존재하지_않는_예매는_404로_변환된다() {
        when(reservationServiceClient.getReservation(1L))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> paymentService.prepare(10L, REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_NOT_FOUND));
    }

    @Test
    void 예매_서비스_장애는_503으로_변환된다() {
        when(reservationServiceClient.getReservation(1L))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> paymentService.prepare(10L, REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE));
    }
}
