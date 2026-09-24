package org.example.reservationservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.example.reservationservice.reservation.repository.PurchaseLimitLockRepository;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-09-21 감사 F5 회귀 테스트 — 같은 사용자가 예매를 동시에 여러 번 보내도 1인당 구매 제한(페스티벌당 4장)을
 * 넘길 수 없어야 한다. 합산과 저장 사이에 잠금이 없으면 모든 요청이 "아직 산 게 없음"을 읽고 통과한다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹한다(재고 차감은 항상 성공).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseLimitConcurrencyAcceptanceTest {

    private static final String CREATE_ENDPOINT = "/api/reservations";
    private static final long FESTIVAL_ID = 1000L;
    private static final long TICKET_TYPE_ID = 1001L;
    private static final long USER_ID = 77L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PurchaseLimitLockRepository purchaseLimitLockRepository;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(festivalServiceClient);
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(new FestivalDetailResponseDto(
                FESTIVAL_ID, 999L, "PUBLISHED",
                java.time.LocalDateTime.now().plusDays(1), java.time.LocalDateTime.now().plusDays(2),
                List.of(new FestivalDetailResponseDto.TicketTypeSummary(TICKET_TYPE_ID, 50000, null, null, "STANDING"))));
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
        purchaseLimitLockRepository.deleteAll();
    }

    private List<MockHttpServletResponse> reserveConcurrently(int requests, int quantity) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return mockMvc.perform(post(CREATE_ENDPOINT)
                                    .header("X-User-Id", String.valueOf(USER_ID))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"festivalId": %d, "ticketTypeId": %d, "quantity": %d}"""
                                            .formatted(FESTIVAL_ID, TICKET_TYPE_ID, quantity)))
                            .andReturn().getResponse();
                }));
            }
            //모든 요청을 한꺼번에 출발시켜 같은 사용자의 예매가 동시에 한도 검사를 하는 상황을 만든다.
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
        }
        return responses;
    }

    private int heldQuantity() {
        return reservationRepository.findByUserId(USER_ID).stream().mapToInt(Reservation::remainingQuantity).sum();
    }

    @Test
    void 같은_사용자가_동시에_여러_번_예매해도_한도를_넘는_예매는_하나도_생기지_않는다() throws Exception {
        //3장씩 5번 동시에 — 한도가 4장이라 한 번만 성공해야 한다.
        List<MockHttpServletResponse> responses = reserveConcurrently(5, 3);

        assertThat(responses).filteredOn(response -> response.getStatus() == 201).hasSize(1);
        assertThat(responses).filteredOn(response -> response.getStatus() == 409)
                .hasSize(4)
                .allSatisfy(response -> assertThat(response.getContentAsString()).contains("PURCHASE_LIMIT_EXCEEDED"));
        assertThat(heldQuantity()).isEqualTo(3);
    }

    @Test
    void 동시에_보내도_한도_안의_예매는_모두_성공한다() throws Exception {
        //2장씩 2번 동시에 — 합쳐서 딱 4장이라 둘 다 성공해야 한다(잠금이 정상 예매까지 막으면 안 된다).
        List<MockHttpServletResponse> responses = reserveConcurrently(2, 2);

        assertThat(responses).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
        assertThat(heldQuantity()).isEqualTo(4);

        //한도에 도달한 뒤의 추가 예매는 거부된다.
        List<MockHttpServletResponse> next = reserveConcurrently(1, 1);
        assertThat(next).singleElement().satisfies(response -> assertThat(response.getStatus()).isEqualTo(409));
    }
}
