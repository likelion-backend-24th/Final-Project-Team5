package org.example.reservationservice.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.example.reservationservice.domain.Reservation;
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

/**
 * 현장 입장 검증(QR·입장 코드) 시나리오. 주최자(HOST)와 도우미(HELPER)가 같은 API를 쓰되
 * 도우미는 배정된 페스티벌 하나만 다룰 수 있다는 점, 그리고 거절 사유가 케이스별로 구분된다는 점을 검증한다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckInAcceptanceTest {

    private static final String VERIFY_ENDPOINT = "/api/organizer/reservations/verify";
    private static final String VERIFY_CODE_ENDPOINT = "/api/organizer/reservations/verify-code";
    private static final String STATS_ENDPOINT = "/api/organizer/reservations/check-in-stats";

    private static final long FESTIVAL_ID = 100L;
    private static final long OTHER_FESTIVAL_ID = 200L;
    private static final long HOST_USER_ID = 999L;
    private static final long HELPER_USER_ID = 555L;
    private static final long TICKET_TYPE_ID = 300L;

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
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(startedFestival(FESTIVAL_ID));
        when(festivalServiceClient.getFestival(OTHER_FESTIVAL_ID)).thenReturn(startedFestival(OTHER_FESTIVAL_ID));
    }

    @Test
    void 도우미가_담당_페스티벌_QR을_스캔하면_입장_처리된다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 2);

        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(reservation.getQrToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reservationId").value(reservation.getId()))
                .andExpect(jsonPath("$.data.checkedInAt").isNotEmpty());

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt()).isNotNull();
    }

    @Test
    void 주최자도_같은_API로_본인_페스티벌_QR을_검증할_수_있다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);

        mockMvc.perform(post(VERIFY_ENDPOINT)
                        .header("X-User-Id", HOST_USER_ID)
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrBody(reservation.getQrToken())))
                .andExpect(status().isOk());

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt()).isNotNull();
    }

    // "이미 입장이 된 티켓의 경우 ... 적절한 문구를 노출하고, 입장 상태로 변경하지 않는다"
    @Test
    void 이미_입장한_티켓은_재입장_처리되지_않는다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);
        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(reservation.getQrToken()))).andExpect(status().isOk());
        Instant firstCheckedInAt = reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt();

        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(reservation.getQrToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CHECKED_IN"));

        //재스캔으로 입장 시각이 덮어써지지 않아야 한다.
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt())
                .isEqualTo(firstCheckedInAt);
    }

    // "다른 공연의 티켓인 경우 ... 적절한 문구를 노출하고, 입장 상태로 변경하지 않는다"
    @Test
    void 다른_공연의_티켓은_입장_처리되지_않는다() throws Exception {
        Reservation otherFestivalTicket = saveConfirmedReservation(OTHER_FESTIVAL_ID, 1);

        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(otherFestivalTicket.getQrToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OTHER_FESTIVAL_TICKET"));

        assertThat(reservationRepository.findById(otherFestivalTicket.getId()).orElseThrow().getCheckedInAt()).isNull();
    }

    // "아직 공연일이 아닌 경우 ... 적절한 문구를 노출하고, 입장 상태로 변경하지 않는다"
    @Test
    void 공연_시작_전에는_입장_처리되지_않는다() throws Exception {
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(upcomingFestival(FESTIVAL_ID));
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);

        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(reservation.getQrToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FESTIVAL_NOT_STARTED"));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt()).isNull();
    }

    @Test
    void 존재하지_않는_QR은_거절된다() throws Exception {
        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody("not-a-real-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVALID_QR_TOKEN"));
    }

    // "QR 실패 시 코드로 확인"
    @Test
    void QR이_안되면_입장_코드로도_입장_처리할_수_있다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);

        mockMvc.perform(helperVerifyCode(FESTIVAL_ID, codeBody(reservation.getCheckInCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservation.getId()));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt()).isNotNull();
    }

    // 현장에서 소문자로 입력하거나 공백이 섞여도 통과해야 한다.
    @Test
    void 입장_코드는_대소문자와_공백에_관계없이_인식된다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);
        String messyCode = "  " + reservation.getCheckInCode().toLowerCase() + " ";

        mockMvc.perform(helperVerifyCode(FESTIVAL_ID, codeBody(messyCode)))
                .andExpect(status().isOk());

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getCheckedInAt()).isNotNull();
    }

    @Test
    void 입장_코드도_다른_공연의_티켓이면_거절된다() throws Exception {
        Reservation otherFestivalTicket = saveConfirmedReservation(OTHER_FESTIVAL_ID, 1);

        mockMvc.perform(helperVerifyCode(FESTIVAL_ID, codeBody(otherFestivalTicket.getCheckInCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OTHER_FESTIVAL_TICKET"));
    }

    @Test
    void 존재하지_않는_입장_코드는_거절된다() throws Exception {
        mockMvc.perform(helperVerifyCode(FESTIVAL_ID, codeBody("ZZ-ZZZZ-ZZZZ")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CHECK_IN_CODE"));
    }

    // 도우미가 쓰는 네 기능 중 "총 티켓 수 대비 현재 입장 인원"
    @Test
    void 총_티켓_수_대비_입장_인원을_조회한다() throws Exception {
        Reservation checkedIn = saveConfirmedReservation(FESTIVAL_ID, 2);
        saveConfirmedReservation(FESTIVAL_ID, 3);
        //다른 페스티벌 티켓은 집계에 섞이면 안 된다.
        saveConfirmedReservation(OTHER_FESTIVAL_ID, 5);
        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(checkedIn.getQrToken()))).andExpect(status().isOk());

        mockMvc.perform(get(STATS_ENDPOINT)
                        .param("festivalId", String.valueOf(FESTIVAL_ID))
                        .header("X-User-Id", HELPER_USER_ID)
                        .header("X-User-Role", "HELPER")
                        .header("X-Festival-Id", FESTIVAL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTickets").value(5))
                .andExpect(jsonPath("$.data.checkedInTickets").value(2));
    }

    @Test
    void 도우미는_담당하지_않는_페스티벌의_입장_현황을_볼_수_없다() throws Exception {
        mockMvc.perform(get(STATS_ENDPOINT)
                        .param("festivalId", String.valueOf(OTHER_FESTIVAL_ID))
                        .header("X-User-Id", HELPER_USER_ID)
                        .header("X-User-Role", "HELPER")
                        .header("X-Festival-Id", FESTIVAL_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OTHER_FESTIVAL_TICKET"));
    }

    @Test
    void 주최자가_아닌_페스티벌의_티켓은_검증할_수_없다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);

        mockMvc.perform(post(VERIFY_ENDPOINT)
                        .header("X-User-Id", HOST_USER_ID + 1) //다른 주최자
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrBody(reservation.getQrToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN_NOT_ORGANIZER"));
    }

    @Test
    void 참가자_권한으로는_입장_검증을_호출할_수_없다() throws Exception {
        Reservation reservation = saveConfirmedReservation(FESTIVAL_ID, 1);

        mockMvc.perform(post(VERIFY_ENDPOINT)
                        .header("X-User-Id", 1L)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrBody(reservation.getQrToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 결제_확정되지_않은_예매는_입장_처리되지_않는다() throws Exception {
        Reservation pending = reservationRepository.save(Reservation.builder()
                .userId(1L)
                .festivalId(FESTIVAL_ID)
                .ticketTypeId(TICKET_TYPE_ID)
                .quantity(1)
                .price(10000)
                .reservationStatus(ReservationStatus.PENDING)
                .qrToken("pending-token")
                .checkInCode("PE-ND-INGX")
                .build());

        mockMvc.perform(helperVerify(FESTIVAL_ID, qrBody(pending.getQrToken())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_CONFIRMED"));
    }

    private Reservation saveConfirmedReservation(Long festivalId, int quantity) {
        Reservation reservation = Reservation.builder()
                .userId(1L)
                .festivalId(festivalId)
                .ticketTypeId(TICKET_TYPE_ID)
                .quantity(quantity)
                .price(10000)
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        //confirm()이 실제로 qrToken·checkInCode를 발급하는 경로를 그대로 태운다.
        reservation.confirm("PAY-" + System.nanoTime(), randomCode());
        return reservationRepository.save(reservation);
    }

    private String randomCode() {
        String raw = java.util.UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return raw.substring(0, 2) + "-" + raw.substring(2, 6) + "-" + raw.substring(6, 10);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder helperVerify(
            long helperFestivalId, String body) {
        return post(VERIFY_ENDPOINT)
                .header("X-User-Id", HELPER_USER_ID)
                .header("X-User-Role", "HELPER")
                .header("X-Festival-Id", helperFestivalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder helperVerifyCode(
            long helperFestivalId, String body) {
        return post(VERIFY_CODE_ENDPOINT)
                .header("X-User-Id", HELPER_USER_ID)
                .header("X-User-Role", "HELPER")
                .header("X-Festival-Id", helperFestivalId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String qrBody(String qrToken) {
        return "{\"qrToken\":\"" + qrToken + "\"}";
    }

    private String codeBody(String checkInCode) {
        return "{\"checkInCode\":\"" + checkInCode + "\"}";
    }

    //페스티벌 시각은 호스트가 입력한 타임존 없는 벽시계라, 서비스가 비교에 쓰는 기준 타임존(app.timezone)과
    //같은 시계로 만들어야 CI처럼 서버 타임존이 다른 환경에서도 결과가 흔들리지 않는다.
    private LocalDateTime festivalClockNow() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    private FestivalDetailResponseDto startedFestival(Long festivalId) {
        return new FestivalDetailResponseDto(festivalId, HOST_USER_ID, "PUBLISHED",
                festivalClockNow().minusHours(1), festivalClockNow().plusHours(5), List.of());
    }

    private FestivalDetailResponseDto upcomingFestival(Long festivalId) {
        return new FestivalDetailResponseDto(festivalId, HOST_USER_ID, "PUBLISHED",
                festivalClockNow().plusDays(1), festivalClockNow().plusDays(2), List.of());
    }
}
