package org.example.festivalservice.festival;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.common.exception.GlobalExceptionHandler;
import org.example.festivalservice.controller.TicketTypeController;
import org.example.festivalservice.domain.tickettype.TicketTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 내부 재고 변경은 인증된 호출에만 허용하고 기존 응답 봉투를 유지한다. */
class TicketTypeInternalTokenTest {

    private TicketTypeService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(TicketTypeService.class);
        TicketTypeController controller = new TicketTypeController(service);
        ReflectionTestUtils.setField(controller, "internalAuthToken", "test-token");
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/stock", "/stock/restore"})
    void 토큰이_없으면_재고를_변경하지_않는다(String path) throws Exception {
        mvc.perform(patch("/internal/v1/ticket-types/1" + path)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INTERNAL_TOKEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/stock", "/stock/restore"})
    void 잘못된_토큰이면_재고를_변경하지_않는다(String path) throws Exception {
        mvc.perform(patch("/internal/v1/ticket-types/1" + path).header("Authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INTERNAL_TOKEN"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/stock", "/stock/restore"})
    void 정상_토큰이면_기존_응답으로_재고를_변경한다(String path) throws Exception {
        mvc.perform(patch("/internal/v1/ticket-types/1" + path).header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        if (path.endsWith("restore")) {
            verify(service).restoreStock(1L, 2);
        } else {
            verify(service).deductStock(1L, 2);
        }
    }
}
