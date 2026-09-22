package org.example.reservationservice.reservation;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 재고 차감과 복구 모두 동일한 내부 토큰을 보내는지 검증한다. */
class FestivalStockClientTest {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 재고_변경_요청에_내부_토큰과_기존_본문을_보낸다(boolean restore) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://festival-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FestivalServiceClient client = new FestivalServiceClient(builder.build());
        ReflectionTestUtils.setField(client, "internalAuthToken", "test-token");
        String path = "/internal/v1/ticket-types/1/stock" + (restore ? "/restore" : "");
        server.expect(requestTo("http://festival-service" + path))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("{\"quantity\":2}"))
                .andRespond(withSuccess());

        if (restore) {
            client.restoreStock(1L, 2);
        } else {
            client.deductStock(1L, 2);
        }

        server.verify();
    }
}
