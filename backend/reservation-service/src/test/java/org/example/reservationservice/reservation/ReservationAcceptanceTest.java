package org.example.reservationservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.reservationservice.domain.ReservationRepository;
import org.example.reservationservice.domain.ReservationStatus;
import org.example.reservationservice.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Story 6 (#30) — 참가자가 티켓 예매를 신청한다. 인수 기준을 그대로 검증한다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹해서 시나리오를 결정적으로 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationAcceptanceTest {

    private static final String CREATE_ENDPOINT = "/api/reservations";
    private static final long FESTIVAL_ID = 100L;
    private static final long TICKET_TYPE_ID = 200L;
    private static final int TICKET_PRICE = 50000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        reset(festivalServiceClient);
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(publishedFestival());
    }

    private FestivalDetailResponseDto publishedFestival() {
        return new FestivalDetailResponseDto(
                FESTIVAL_ID,
                999L,
                "PUBLISHED",
                List.of(new FestivalDetailResponseDto.TicketTypeSummary(TICKET_TYPE_ID, TICKET_PRICE))
        );
    }

    private String createRequestBody(long ticketTypeId, int quantity) {
        return """
                {
                  "festivalId": %d,
                  "ticketTypeId": %d,
                  "quantity": %d
                }""".formatted(FESTIVAL_ID, ticketTypeId, quantity);
    }

    // "참가자가 남은 재고 이내의 수량으로 신청하면 201과 함께 Pending 상태의 예매가 생성된다"
    @Test
    void 재고_이내로_신청하면_201과_함께_PENDING_예매가_생성된다() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(TICKET_TYPE_ID, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reservationStatus", org.hamcrest.Matchers.is("PENDING")))
                .andExpect(jsonPath("$.data.quantity", org.hamcrest.Matchers.is(2)))
                .andExpect(jsonPath("$.data.totalAmount", org.hamcrest.Matchers.is(TICKET_PRICE * 2)));

        verify(festivalServiceClient).deductStock(TICKET_TYPE_ID, 2);
    }

    // "신청 수량이 남은 재고를 초과하면 409를 반환하고 예매가 생성되지 않는다"
    // (매진된 티켓 종류에 대한 신청 차단도 festival-service가 같은 409로 응답하는 동일 경로다)
    @Test
    void 재고를_초과해_신청하면_409를_반환하고_예매가_생성되지_않는다() throws Exception {
        // 1인당 구매 제한(기본 4장) 이내의 수량으로, 재고 부족만을 원인으로 삼는다.
        doThrow(HttpClientErrorException.create(
                org.springframework.http.HttpStatus.CONFLICT, "Conflict", null, null, null))
                .when(festivalServiceClient).deductStock(TICKET_TYPE_ID, 2);

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(TICKET_TYPE_ID, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("STOCK_EXCEEDED")));

        assertThat(reservationRepository.findByUserId(1L)).isEmpty();
    }

    // "동시에 여러 요청이 몰려도 실제 재고보다 많은 수량이 승인되지 않는다"
    // 재고의 원자적 차감 자체는 festival-service 책임이므로, 여기서는 그 원자성을 흉내낸 모킹(남은 재고를
    // 공유 카운터로 관리)으로 reservation-service가 차감 실패(409) 응답을 받은 요청에 대해 절대
    // 예매를 만들지 않는지를 검증한다 — 승인된 예매 수가 재고를 절대 넘지 않아야 한다.
    @Test
    void 동시_요청이_몰려도_재고보다_많은_예매가_승인되지_않는다() throws Exception {
        int stock = 3;
        int concurrentRequests = 10;
        AtomicInteger remainingStock = new AtomicInteger(stock);

        // festival-service의 실제 원자적 UPDATE(WHERE remain_quantity >= ?)를 흉내낸다.
        doAnswer(invocation -> {
            int quantity = invocation.getArgument(1);
            int updated = remainingStock.updateAndGet(current -> current - quantity);
            if (updated < 0) {
                remainingStock.addAndGet(quantity);
                throw HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.CONFLICT, "Conflict", null, null, null);
            }
            return null;
        }).when(festivalServiceClient).deductStock(eq(TICKET_TYPE_ID), anyInt());

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrentRequests);

        for (int i = 0; i < concurrentRequests; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    mockMvc.perform(post(CREATE_ENDPOINT)
                            .header("X-User-Id", String.valueOf(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createRequestBody(TICKET_TYPE_ID, 1)));
                } catch (Exception ignored) {
                    // 결과 검증은 DB 상태로 한다
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long approvedCount = reservationRepository.findAll().stream()
                .filter(r -> r.getReservationStatus() == ReservationStatus.PENDING)
                .count();
        assertThat(approvedCount).isEqualTo(stock);
    }

    // "존재하지 않거나 비공개(미승인) 페스티벌의 티켓에 신청하면 404를 반환한다" — 존재하지 않는 경우
    @Test
    void 존재하지_않는_페스티벌에_신청하면_404를_반환한다() throws Exception {
        long unknownFestivalId = 999L;
        when(festivalServiceClient.getFestival(unknownFestivalId))
                .thenThrow(HttpClientErrorException.create(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "festivalId": %d,
                                  "ticketTypeId": %d,
                                  "quantity": 1
                                }""".formatted(unknownFestivalId, TICKET_TYPE_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("FESTIVAL_NOT_PUBLISHED")));
    }

    // "존재하지 않거나 비공개(미승인) 페스티벌의 티켓에 신청하면 404를 반환한다" — 비공개(미승인)인 경우
    @Test
    void 비공개_페스티벌에_신청하면_404를_반환한다() throws Exception {
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(new FestivalDetailResponseDto(
                FESTIVAL_ID, 999L, "PENDING",
                List.of(new FestivalDetailResponseDto.TicketTypeSummary(TICKET_TYPE_ID, TICKET_PRICE))));

        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(TICKET_TYPE_ID, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("FESTIVAL_NOT_PUBLISHED")));

        verify(festivalServiceClient, never()).deductStock(anyLong(), anyInt());
    }

    // "본인은 자신의 예매 내역을 조회할 수 있고, 타인의 예매는 조회할 수 없으며 403을 반환한다"
    @Test
    void 본인_예매만_조회할_수_있고_타인_예매_조회는_403을_반환한다() throws Exception {
        String location = mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(TICKET_TYPE_ID, 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long reservationId = reservationRepository.findByUserId(1L).get(0).getId();

        mockMvc.perform(get(CREATE_ENDPOINT + "/" + reservationId).header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", org.hamcrest.Matchers.is(reservationId.intValue())));

        mockMvc.perform(get(CREATE_ENDPOINT + "/" + reservationId).header("X-User-Id", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("FORBIDDEN_NOT_OWNER")));
    }

    // "인증 없이 예매 신청·조회 API에 접근하면 401을 반환한다"
    @Test
    void 인증_없이_예매_신청_조회에_접근하면_401을_반환한다() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(TICKET_TYPE_ID, 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("UNAUTHORIZED")));

        mockMvc.perform(get(CREATE_ENDPOINT + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("UNAUTHORIZED")));
    }
}
