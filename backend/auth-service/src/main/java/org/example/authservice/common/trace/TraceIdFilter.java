package org.example.authservice.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 게이트웨이(TraceIdGlobalFilter)가 붙인 X-Trace-Id를 요청을 처리하는 동안 로그 MDC(traceId)에 싣고 응답 헤더로 돌려준다.
 * 다른 서비스로 보내는 내부 호출은 TraceIdPropagationInterceptor가 같은 값을 이어 붙여, 한 사용자 요청의 로그를 서비스 간에 묶어 볼 수 있다.
 * 게이트웨이를 거치지 않은 요청(스케줄러발 내부 호출 등)처럼 값이 없거나 형식이 다르면 여기서 새로 만든다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    //로그에 그대로 찍히는 값이라 줄바꿈 등으로 로그를 위조하지 못하게 형식을 제한한다.
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = incoming != null && ALLOWED.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
