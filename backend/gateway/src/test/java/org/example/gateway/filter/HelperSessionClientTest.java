package org.example.gateway.filter;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HelperSessionClientTest {
    HttpServer server;
    HelperSessionClient client;
    AtomicReference<String> response = new AtomicReference<>("{\"success\":true,\"data\":true}");
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> query = new AtomicReference<>();
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/helper-accounts/session", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        client = new HelperSessionClient("http://127.0.0.1:" + server.getAddress().getPort(), "test-internal");
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void checksCurrentSessionWithInternalBearer() {
        assertThat(client.isValid(20, 7, 3).block()).isTrue();
        assertThat(authorization.get()).isEqualTo("Bearer test-internal");
        assertThat(query.get()).isEqualTo("userId=20&festivalId=7&version=3");
    }
    @Test void rejectsDeniedOrMalformedResponses() {
        response.set("{\"success\":false,\"data\":true}");
        assertThat(client.isValid(20, 7, 3).block()).isFalse();
        response.set("not-json");
        assertThat(client.isValid(20, 7, 3).block()).isFalse();
    }
    @Test void failsClosedWhenAuthServiceIsUnavailable() {
        server.stop(0);
        assertThat(client.isValid(20, 7, 3).block()).isFalse();
    }
}
