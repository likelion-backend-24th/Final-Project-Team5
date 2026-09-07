package org.example.paymentservice.infrastructure.reservation;

import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
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
}
