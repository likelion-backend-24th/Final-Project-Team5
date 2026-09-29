package org.example.reservationservice.common.trace;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 다른 서비스로 보내는 내부 호출에 현재 요청의 X-Trace-Id를 이어 붙인다.
 * 서비스 간 RestClient에만 등록하고, PortOne·Gemini 같은 외부 API 호출에는 붙이지 않는다.
 * 스케줄러처럼 사용자 요청 밖에서 부르면 MDC가 비어 있어 헤더를 붙이지 않고, 받는 쪽 TraceIdFilter가 새로 만든다.
 */
public class TraceIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null && !request.getHeaders().containsKey(TraceIdFilter.TRACE_ID_HEADER)) {
            request.getHeaders().set(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
