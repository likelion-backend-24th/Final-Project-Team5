package org.example.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every request carries an X-Trace-Id, generating one when the caller didn't send one,
 * and echoes it back on the response so client and logs can be correlated across services.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String incoming = request.getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (incoming == null || incoming.isBlank()) ? UUID.randomUUID().toString() : incoming;

        ServerHttpRequest tracedRequest = request.mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        return chain.filter(exchange.mutate().request(tracedRequest).build());
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
