package org.example.paymentservice.infrastructure.reservation;

import org.example.paymentservice.infrastructure.reservation.dto.CancelReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ExtendReservationHoldRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReservationServiceClientTest {

    private MockRestServiceServer server;
    private ReservationServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://reservation-service")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ReservationServiceClient(builder.build());
    }

    @Test
    void 예매_조회에_성공하면_응답을_파싱한다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "reservationId": 1,
                          "userId": 10,
                          "status": "PENDING",
                          "totalAmount": 10000,
                          "ticketTypeId": 2,
                          "quantity": 1,
                          "expiresAt": "2026-09-07T08:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        ReservationForPaymentResponse response = client.getReservation(1L);

        assertThat(response.reservationId()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.totalAmount()).isEqualTo(10000L);
        server.verify();
    }

    @Test
    void 존재하지_않는_예매_조회는_404_예외를_던진다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getReservation(999L))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void 예매_확정을_요청한다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/1/confirm"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess());

        client.confirmReservation(1L, new ConfirmReservationRequest("BE24-T05-1", 10_000L, "CARD", Instant.now()));

        server.verify();
    }

    @Test
    void 이미_만료된_예매의_확정_요청은_409_예외를_던진다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/1/confirm"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.confirmReservation(1L,
                new ConfirmReservationRequest("BE24-T05-1", 10_000L, "CARD", Instant.now())))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
    }

    @Test
    void 예매_취소를_요청한다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/1/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andRespond(withSuccess());

        client.cancelReservation(1L, new CancelReservationRequest("BE24-T05-1", "PAYMENT_FAILED"));

        server.verify();
    }

    @Test
    void 가상계좌_홀드_연장을_요청한다() {
        server.expect(requestTo("http://reservation-service/internal/v1/reservations/1/extend-hold"))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andRespond(withSuccess());

        client.extendReservationHold(1L, new ExtendReservationHoldRequest(Instant.parse("2026-09-08T07:32:37Z")));

        server.verify();
    }
}
