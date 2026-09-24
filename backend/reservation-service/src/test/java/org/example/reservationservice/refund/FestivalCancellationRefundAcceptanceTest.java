package org.example.reservationservice.refund;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-09-24 추가 발견 C1 회귀 테스트 — 주최자 귀책 행사 취소가 진행 중(CANCELLATION_PENDING)이거나 끝난(CANCELLED)
 * 행사의 예매는 본인 환불(위약금 공제)을 받을 수 없고, 운영자 승인 후 주최자 귀책 전액 환불만 가능해야 한다.
 * 화면에서 버튼을 숨기는 것만으로는 API 직접 호출을 막지 못하므로 견적 단계에서 서버가 거절하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FestivalCancellationRefundAcceptanceTest {

    private static final long FESTIVAL_ID = 700L;
    private static final long USER_ID = 1L;
    private static final String INTERNAL_TOKEN = "Bearer test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(festivalServiceClient);
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll();
    }

    private Reservation confirmedReservation() {
        return reservationRepository.save(Reservation.builder()
                .userId(USER_ID).festivalId(FESTIVAL_ID).hostUserId(10L).ticketTypeId(800L)
                .price(30000).quantity(2).reservationStatus(ReservationStatus.CONFIRMED).paymentId("cancel-refund-p")
                .qrToken(java.util.UUID.randomUUID().toString())
                .build());
    }

    //공연은 30일 뒤라, 행사 상태만 아니면 위약금 0%로 환불 가능한 예매다.
    private void festivalIs(String festivalStatus) {
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(new FestivalDetailResponseDto(
                FESTIVAL_ID, 10L, festivalStatus,
                LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(31), List.of()));
    }

    @Test
    void 행사_취소가_진행_중이면_참가자_본인_환불_견적이_거절된다() throws Exception {
        Reservation reservation = confirmedReservation();
        festivalIs("CANCELLATION_PENDING");

        mockMvc.perform(get("/api/reservations/" + reservation.getId() + "/refund-quote").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundable").value(false))
                .andExpect(jsonPath("$.data.rejectReason").value("FESTIVAL_CANCELLATION_REFUND_PENDING"));
    }

    @Test
    void 결제_서비스가_받는_내부_환불_견적도_거절돼_API_직접_호출로_위약금_환불을_받을_수_없다() throws Exception {
        Reservation reservation = confirmedReservation();

        for (String festivalStatus : List.of("CANCELLATION_PENDING", "CANCELLED")) {
            festivalIs(festivalStatus);

            mockMvc.perform(get("/internal/v1/reservations/" + reservation.getId() + "/refund-quote")
                            .header("Authorization", INTERNAL_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refundable").value(false))
                    .andExpect(jsonPath("$.rejectReason").value("FESTIVAL_CANCELLATION_REFUND_PENDING"));
        }
    }

    @Test
    void 행사_취소_중에도_주최자_귀책_전액_환불_견적은_그대로_나온다() throws Exception {
        Reservation reservation = confirmedReservation();
        festivalIs("CANCELLATION_PENDING");

        mockMvc.perform(get("/internal/v1/reservations/" + reservation.getId() + "/organizer-refund-quote")
                        .header("Authorization", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundable").value(true))
                .andExpect(jsonPath("$.feePercent").value(0))
                .andExpect(jsonPath("$.refundAmount").value(60000));
    }

    @Test
    void 공개_중인_행사는_기존처럼_본인_환불_견적이_나온다() throws Exception {
        Reservation reservation = confirmedReservation();
        festivalIs("PUBLISHED");

        mockMvc.perform(get("/api/reservations/" + reservation.getId() + "/refund-quote").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundable").value(true))
                .andExpect(jsonPath("$.data.feePercent").value(0));
    }
}
