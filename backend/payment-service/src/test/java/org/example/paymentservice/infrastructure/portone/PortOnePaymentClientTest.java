package org.example.paymentservice.infrastructure.portone;

import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOnePaymentClientTest {

    private MockRestServiceServer server;
    private PortOnePaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne test-secret");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
    }

    @Test
    void 결제_단건_조회에_성공하면_응답을_파싱한다() {
        server.expect(requestTo("https://api.portone.io/payments/BE24-T05-1"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
                .andRespond(withSuccess("""
                        {
                          "id": "BE24-T05-1",
                          "status": "PAID",
                          "transactionId": "TX-1",
                          "storeId": "store-04f7a059-9b5d-4bb8-ac93-f35434438216",
                          "channel": {"id": "channel-1", "key": "channel-key-c5723eb4-9ee3-4df3-9c56-129d13d4e9d6", "type": "LIVE"},
                          "amount": {"total": 10000, "cancelled": 0},
                          "currency": "KRW",
                          "requestedAt": "2026-09-07T00:00:00Z",
                          "updatedAt": "2026-09-07T00:00:01Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        PortOnePaymentResponse response = client.getPayment("BE24-T05-1");

        assertThat(response.id()).isEqualTo("BE24-T05-1");
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.amount().total()).isEqualTo(10000);
        server.verify();
    }

    @Test
    void 존재하지_않는_결제_조회는_404_예외를_던진다() {
        server.expect(requestTo("https://api.portone.io/payments/UNKNOWN"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getPayment("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void PortOne_서버_오류는_5xx_예외를_던진다() {
        server.expect(requestTo("https://api.portone.io/payments/BE24-T05-2"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getPayment("BE24-T05-2"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
