package org.example.authservice.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게이트웨이가 붙인 X-Trace-Id가 요청 처리 중 로그 MDC에 실리고, 처리가 끝나면 지워지는지 확인한다.
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String traceIdSeenDuring(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(TraceIdFilter.MDC_KEY));
            }
        });
        return seen.get();
    }

    @Test
    void 게이트웨이가_준_추적_ID를_처리하는_동안_로그에_싣고_끝나면_지운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "3f2c1a9e-trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(traceIdSeenDuring(request, response)).isEqualTo("3f2c1a9e-trace");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void 추적_ID가_없거나_형식이_다르면_새로_만든다() throws Exception {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        MockHttpServletRequest forged = new MockHttpServletRequest();
        forged.addHeader(TraceIdFilter.TRACE_ID_HEADER, "bad\nINFO forged log line");
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();

        String generated = traceIdSeenDuring(missing, missingResponse);
        String replaced = traceIdSeenDuring(forged, forgedResponse);

        assertThat(generated).isNotBlank().matches("[A-Za-z0-9-]{1,64}");
        assertThat(replaced).isNotBlank().doesNotContain("forged");
    }
}
