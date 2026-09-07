package org.example.paymentservice.infrastructure.portone;

import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
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

    private static final String STORE_ID = "store-04f7a059-9b5d-4bb8-ac93-f35434438216";

    private MockRestServiceServer server;
    private PortOnePaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.portone.io")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne test-secret");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOnePaymentClient(builder.build());
        ReflectionTestUtils.setField(client, "storeId", STORE_ID);
    }

    // 2026-09-07 실제 테스트 채널 결제(PAID)로 확인한 실물 응답을 기준으로 구성.
    // storeId 쿼리 파라미터를 빼면 PortOne이 PAYMENT_NOT_FOUND(404)를 반환하는 것도 실제로 확인했다.
    @Test
    void 결제_단건_조회에_성공하면_응답을_파싱한다() {
        server.expect(requestTo("https://api.portone.io/payments/BE24-T05-1?storeId=" + STORE_ID))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "PortOne test-secret"))
                .andRespond(withSuccess("""
                        {
                          "status": "PAID",
                          "id": "BE24-T05-1",
                          "transactionId": "01a07ab6-3bbe-f28f-9452-4e0baa5d1120",
                          "storeId": "store-04f7a059-9b5d-4bb8-ac93-f35434438216",
                          "channel": {
                            "type": "TEST",
                            "id": "channel-id-531b3cf3-46e3-456e-84b1-809529329165",
                            "key": "channel-key-c5723eb4-9ee3-4df3-9c56-129d13d4e9d6",
                            "name": "토스페이먼츠_일반",
                            "pgProvider": "TOSSPAYMENTS"
                          },
                          "orderName": "스키마 확인용 테스트 결제",
                          "amount": {"total": 1000, "taxFree": 0, "vat": 91, "supply": 909, "discount": 0, "paid": 1000, "cancelled": 0, "cancelledTaxFree": 0},
                          "currency": "KRW",
                          "requestedAt": "2026-09-07T07:12:38.366979621Z",
                          "updatedAt": "2026-09-07T07:13:26.606557991Z",
                          "statusChangedAt": "2026-09-07T07:13:26Z",
                          "paidAt": "2026-09-07T07:13:26Z",
                          "pgTxId": "tiamp20260907161239ikP79"
                        }
                        """, MediaType.APPLICATION_JSON));

        PortOnePaymentResponse response = client.getPayment("BE24-T05-1");

        assertThat(response.id()).isEqualTo("BE24-T05-1");
        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.amount().total()).isEqualTo(1000);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.channel().type()).isEqualTo("TEST");
        server.verify();
    }

    @Test
    void 결제_실패_응답은_실패_사유를_담는다() {
        server.expect(requestTo("https://api.portone.io/payments/BE24-T05-2?storeId=" + STORE_ID))
                .andRespond(withSuccess("""
                        {
                          "status": "FAILED",
                          "id": "BE24-T05-2",
                          "transactionId": "01a07a78-ca2c-538a-2896-2a6d90220331",
                          "storeId": "store-04f7a059-9b5d-4bb8-ac93-f35434438216",
                          "channel": {"type": "TEST", "id": "channel-id-1", "key": "channel-key-1", "name": "토스페이먼츠_일반", "pgProvider": "TOSSPAYMENTS"},
                          "amount": {"total": 50000, "taxFree": 0, "vat": 4545, "supply": 45455, "discount": 0, "paid": 50000, "cancelled": 0, "cancelledTaxFree": 0},
                          "currency": "KRW",
                          "requestedAt": "2026-09-07T06:05:31Z",
                          "updatedAt": "2026-09-07T06:05:44Z",
                          "statusChangedAt": "2026-09-07T06:05:44Z",
                          "failedAt": "2026-09-07T06:05:44Z",
                          "failure": {"reason": "[PAY_PROCESS_CANCELED] 사용자가 결제를 취소하였습니다", "pgCode": "PAY_PROCESS_CANCELED", "pgMessage": "사용자가 결제를 취소하였습니다"}
                        }
                        """, MediaType.APPLICATION_JSON));

        PortOnePaymentResponse response = client.getPayment("BE24-T05-2");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failure().pgCode()).isEqualTo("PAY_PROCESS_CANCELED");
    }

    @Test
    void 존재하지_않는_결제_조회는_404_예외를_던진다() {
        server.expect(requestTo("https://api.portone.io/payments/UNKNOWN?storeId=" + STORE_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getPayment("UNKNOWN"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void PortOne_서버_오류는_5xx_예외를_던진다() {
        server.expect(requestTo("https://api.portone.io/payments/BE24-T05-3?storeId=" + STORE_ID))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getPayment("BE24-T05-3"))
                .isInstanceOf(HttpServerErrorException.class);
    }
}
