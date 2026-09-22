package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.festivalservice.controller.FestivalController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

/** 조회수 집계는 nginx의 연결 IP를 우선하고 기존 프록시 없는 요청도 처리한다. */
class FestivalClientIpTest {

    @Test
    void nginx가_전달한_IP를_외부_전달_목록보다_우선한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", " 192.0.2.10 ");
        assertThat(clientIp("198.51.100.1, 192.0.2.10", request)).isEqualTo("192.0.2.10");
    }

    @Test
    void 실제_IP_헤더가_없으면_기존_전달_목록을_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", " ");
        assertThat(clientIp("198.51.100.1, 192.0.2.10", request)).isEqualTo("198.51.100.1");
    }

    @Test
    void 프록시_헤더가_없으면_연결_IP를_사용한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.20");
        assertThat(clientIp(null, request)).isEqualTo("192.0.2.20");
    }

    private String clientIp(String forwardedFor, MockHttpServletRequest request) {
        return ReflectionTestUtils.invokeMethod(FestivalController.class, "clientIp", forwardedFor, request);
    }
}
