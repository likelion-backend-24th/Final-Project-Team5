package org.example.paymentservice.domain.payment;

import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.CancelReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 7-7 — 결제 정상·실패·중복·변조 시나리오를 실제 HTTP 요청(MockMvc)과 H2 DB로 검증한다.
 * PortOne·Reservation-Service는 실제로 띄우지 않고 Client 계층(PortOnePaymentClient,
 * ReservationServiceClient)만 모킹해서 외부 경계만 대역으로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAcceptanceTest {

    private static final String TEST_WEBHOOK_SECRET = "whsec_dGVzdC13ZWJob29rLXNlY3JldA==";
    private static final Long USER_ID = 1L;
    private static final Long RESERVATION_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockitoBean
    private ReservationServiceClient reservationServiceClient;

    @MockitoBean
    private PortOnePaymentClient portOnePaymentClient;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        paymentRepository.deleteAll();
        reset(reservationServiceClient, portOnePaymentClient);
        when(reservationServiceClient.getReservation(RESERVATION_ID))
                .thenReturn(new ReservationForPaymentResponse(RESERVATION_ID, USER_ID, "PENDING", 10_000L, 2L, 1,
                        Instant.now().plus(10, ChronoUnit.MINUTES)));
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

    private PortOnePaymentResponse.Amount amount(long total) {
        return new PortOnePaymentResponse.Amount(total, 0, 0, total, 0, total, 0, 0);
    }

    private PortOnePaymentResponse paidResponse(String paymentId, String transactionId, long total) {
        return new PortOnePaymentResponse(paymentId, "PAID", transactionId, "store-test", channel(),
                new PortOnePaymentResponse.Method("PaymentMethodCard", null, null, null, null, null, null),
                amount(total), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), Instant.now(), null, null, "pgtx-1");
    }

    private PortOnePaymentResponse failedResponse(String paymentId) {
        return new PortOnePaymentResponse(paymentId, "FAILED", "TX-FAILED", "store-test", channel(), null,
                amount(10_000L), "KRW", "테스트 결제", Instant.now(), Instant.now(), Instant.now(), null, Instant.now(),
                new PortOnePaymentResponse.Failure("사용자가 결제를 취소하였습니다", "PAY_PROCESS_CANCELED", "사용자가 결제를 취소하였습니다"), "pgtx-2");
    }

    private String sign(String id, long timestamp, String body) throws Exception {
        String key = TEST_WEBHOOK_SECRET.startsWith("whsec_") ? TEST_WEBHOOK_SECRET.substring(6) : TEST_WEBHOOK_SECRET;
        byte[] secretBytes = Base64.getDecoder().decode(key);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        byte[] sig = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(sig);
    }

    private void postWebhook(String webhookId, String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signature = sign(webhookId, timestamp, body);
        mockMvc.perform(post("/api/v1/webhooks/portone")
                        .header("webhook-id", webhookId)
                        .header("webhook-timestamp", String.valueOf(timestamp))
                        .header("webhook-signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String paidWebhookBody(String paymentId, String transactionId) {
        return "{\"type\":\"Transaction.Paid\",\"timestamp\":\"2026-09-07T00:00:00Z\",\"data\":{\"paymentId\":\""
                + paymentId + "\",\"storeId\":\"store-test\",\"transactionId\":\"" + transactionId + "\"}}";
    }

    @Test
    void 일반결제_성공_시나리오() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(paidResponse(paymentId, "TX-1", 10_000L));

        mockMvc.perform(post("/api/payments/" + paymentId + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));

        verify(reservationServiceClient).confirmReservation(org.mockito.ArgumentMatchers.eq(RESERVATION_ID), any(ConfirmReservationRequest.class));
    }

    @Test
    void 일반결제_실패_시나리오() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(failedResponse(paymentId));

        mockMvc.perform(post("/api/payments/" + paymentId + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        verify(reservationServiceClient).cancelReservation(org.mockito.ArgumentMatchers.eq(RESERVATION_ID), any(CancelReservationRequest.class));
    }

    @Test
    void 금액이_변조되면_확정을_거부한다() throws Exception {
        String paymentId = preparePayment();
        // PortOne 조회 결과 금액(1,000)이 준비 시점 주문금액(10,000)과 다르다 — 프론트가 위조한 상황을 흉내낸다.
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(paidResponse(paymentId, "TX-1", 1_000L));

        mockMvc.perform(post("/api/payments/" + paymentId + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_VERIFICATION_FAILED"));

        verify(reservationServiceClient, never()).confirmReservation(anyLong(), any());
    }

    @Test
    void 완료_API_호출_없이_웹훅만으로_결제가_확정된다() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(paidResponse(paymentId, "TX-1", 10_000L));

        // 사용자가 브라우저를 닫아 /complete를 호출하지 않았다고 가정하고, 웹훅만 보낸다.
        postWebhook("webhook-1", paidWebhookBody(paymentId, "TX-1"));

        verify(reservationServiceClient).confirmReservation(org.mockito.ArgumentMatchers.eq(RESERVATION_ID), any(ConfirmReservationRequest.class));
        assertPaymentStatus(paymentId, PaymentStatus.PAID);
    }

    @Test
    void 같은_웹훅이_반복돼도_예매_확정은_한_번만_호출된다() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(paidResponse(paymentId, "TX-1", 10_000L));

        String body = paidWebhookBody(paymentId, "TX-1");
        postWebhook("webhook-dup", body);
        postWebhook("webhook-dup", body); // 같은 webhook-id로 재전송(PortOne 재시도 상황)

        verify(reservationServiceClient, org.mockito.Mockito.times(1))
                .confirmReservation(org.mockito.ArgumentMatchers.eq(RESERVATION_ID), any(ConfirmReservationRequest.class));
    }

    @Test
    void 예매가_이미_만료된_뒤_도착한_확정_요청은_거부된다() throws Exception {
        String paymentId = preparePayment();
        when(portOnePaymentClient.getPayment(paymentId)).thenReturn(paidResponse(paymentId, "TX-1", 10_000L));
        doThrow(HttpClientErrorException.Conflict.create(org.springframework.http.HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(reservationServiceClient).confirmReservation(anyLong(), any());

        mockMvc.perform(post("/api/payments/" + paymentId + "/complete").header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_ALREADY_FINALIZED"));
    }

    private void assertPaymentStatus(String paymentId, PaymentStatus expected) {
        Payment payment = paymentRepository.findByPaymentId(paymentId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(expected);
    }
}
