package org.example.paymentservice.domain.payment;

import org.example.paymentservice.domain.cancellation.CancellationRepository;
import org.example.paymentservice.domain.cancellation.PaymentCancellationService;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2026-09-21 감사 F7 회귀 테스트 — PG 승인은 끝났는데 예매 확정이 거절된 결제(같은 예매를 두 탭에서 결제,
 * 결제 도중 예매 만료 등)는 돈만 빠져나간 채 남지 않고 자동 전액 환불돼야 한다(실전 가이드 7.4·11.4·13.3-25).
 * PaymentAcceptanceTest처럼 PortOne·Reservation-Service Client 계층만 모킹하고 실제 HTTP 요청과 H2 DB로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentCompensationAcceptanceTest {

    private static final Long USER_ID = 1L;
    private static final Long RESERVATION_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private CancellationRepository cancellationRepository;

    @Autowired
    private PaymentCancellationService paymentCancellationService;

    @MockitoBean
    private ReservationServiceClient reservationServiceClient;

    @MockitoBean
    private PortOnePaymentClient portOnePaymentClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(reservationServiceClient, portOnePaymentClient);
        when(reservationServiceClient.getReservation(RESERVATION_ID))
                .thenReturn(new ReservationForPaymentResponse(RESERVATION_ID, USER_ID, "PENDING", 10_000L, 2L, 1,
                        Instant.now().plus(10, ChronoUnit.MINUTES)));
    }

    //결제를 참조하는 취소·거래 기록을 먼저 지운다. 같은 H2 컨텍스트를 쓰는 PaymentAcceptanceTest는 결제만 지우므로 끝날 때도 정리한다.
    @AfterEach
    void cleanUp() {
        cancellationRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private String preparePayment() throws Exception {
        String body = mockMvc.perform(post("/api/payments/prepare")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationId\":" + RESERVATION_ID + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"paymentId\":\"([^\"]+)\".*", "$1");
    }

    private PortOnePaymentResponse.Channel channel() {
        return new PortOnePaymentResponse.Channel("channel-id-1", "channel-key-test", "TEST", "토스페이먼츠_일반", "TOSSPAYMENTS");
    }

    private PortOnePaymentResponse paidResponse(String paymentId, String transactionId) {
        return new PortOnePaymentResponse(paymentId, "PAID", transactionId, "store-test", channel(),
                new PortOnePaymentResponse.Method("CARD", null, null, null, null, null, null),
                new PortOnePaymentResponse.Amount(10_000L, 0, 0, 10_000L, 0, 10_000L, 0, 0), "KRW", "테스트 결제",
                Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-1", null);
    }

    private PortOnePaymentResponse cancelledResponse(String paymentId, String transactionId, PortOnePaymentResponse.Cancellation cancellation) {
        return new PortOnePaymentResponse(paymentId, "CANCELLED", transactionId, "store-test", channel(),
                new PortOnePaymentResponse.Method("CARD", null, null, null, null, null, null),
                new PortOnePaymentResponse.Amount(10_000L, 0, 0, 10_000L, 0, 10_000L, 10_000L, 0), "KRW", "테스트 결제",
                Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-1", List.of(cancellation));
    }

    private PortOnePaymentResponse.Cancellation succeededRefund() {
        return new PortOnePaymentResponse.Cancellation("cancel-comp", "SUCCEEDED", null, 10_000L, Instant.now(), Instant.now());
    }

    private void rejectConfirmOf(String paymentId) {
        doThrow(HttpClientErrorException.Conflict.create(HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(reservationServiceClient).confirmReservation(eq(RESERVATION_ID),
                        argThat(request -> paymentId.equals(request.paymentId())));
    }

    private void assertPaymentStatus(String paymentId, PaymentStatus expected) {
        assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo(expected);
    }

    @Test
    void 같은_예매를_두_번_결제하면_나중에_확정되는_결제는_자동_환불된다() throws Exception {
        // 두 탭에서 같은 예매를 결제한 상황 — 두 결제 모두 PortOne 승인까지 끝났다.
        String first = preparePayment();
        String second = preparePayment();
        when(portOnePaymentClient.getPayment(first)).thenReturn(paidResponse(first, "TX-1"));
        when(portOnePaymentClient.getPayment(second)).thenReturn(
                paidResponse(second, "TX-2"),
                cancelledResponse(second, "TX-2", succeededRefund()));
        when(portOnePaymentClient.cancelPayment(eq(second), eq(10_000L), any(), eq("COMPENSATE-" + second)))
                .thenReturn(new PortOneCancelResponse(succeededRefund()));
        // 먼저 확정된 결제가 있으니 예매 서비스는 나중 결제의 확정을 409로 거절한다.
        rejectConfirmOf(second);

        mockMvc.perform(post("/api/payments/" + first + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/payments/" + second + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_ALREADY_FINALIZED"));

        assertPaymentStatus(first, PaymentStatus.PAID);
        assertPaymentStatus(second, PaymentStatus.CANCELLED);
        assertThat(paymentRepository.findByPaymentId(second).orElseThrow().isReservationRejected()).isTrue();

        // 브라우저가 완료 API를 다시 불러도 성공으로 바뀌지 않고, PortOne 취소도 다시 요청하지 않는다.
        mockMvc.perform(post("/api/payments/" + second + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_ALREADY_FINALIZED"));
        verify(portOnePaymentClient, times(1)).cancelPayment(any(), anyLong(), any(), any());
        // 예매에 반영된 적 없는 결제라 예매 서비스에 환불을 알리지 않는다.
        verify(reservationServiceClient, never()).refundReservation(anyLong(), any());
    }

    @Test
    void 자동_환불_요청이_실패해도_거절_표시가_남아_보상_재시도로_환불이_끝난다() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(
                paidResponse(paymentId, "TX-1"),
                cancelledResponse(paymentId, "TX-1", succeededRefund()));
        when(portOnePaymentClient.cancelPayment(eq(paymentId), eq(10_000L), any(), eq("COMPENSATE-" + paymentId)))
                .thenThrow(new ResourceAccessException("PortOne timeout"))
                .thenReturn(new PortOneCancelResponse(succeededRefund()));
        // 결제 도중 예매가 만료 배치로 취소된 상황.
        rejectConfirmOf(paymentId);

        mockMvc.perform(post("/api/payments/" + paymentId + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_ALREADY_FINALIZED"));
        // 환불 요청이 타임아웃이라 돈은 아직 돌아가지 않았다 — 거절 표시와 PAID가 남아 보상 배치의 대상이 된다.
        assertPaymentStatus(paymentId, PaymentStatus.PAID);
        assertThat(paymentRepository.findByStatusInAndReservationRejectedAtBefore(
                List.of(PaymentStatus.PAID), Instant.now().plusSeconds(1)))
                .extracting(Payment::getPaymentId).containsExactly(paymentId);

        // PaymentCompensationScheduler가 하는 재시도 — 같은 멱등키로 다시 요청해 환불을 끝낸다.
        boolean refunded = paymentCancellationService.compensate(paymentRepository.findByPaymentId(paymentId).orElseThrow());

        assertThat(refunded).isTrue();
        assertPaymentStatus(paymentId, PaymentStatus.CANCELLED);
        verify(portOnePaymentClient, times(2)).cancelPayment(eq(paymentId), eq(10_000L), any(), eq("COMPENSATE-" + paymentId));
    }
}
