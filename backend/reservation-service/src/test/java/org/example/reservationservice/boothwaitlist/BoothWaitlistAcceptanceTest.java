package org.example.reservationservice.boothwaitlist;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistCounterRepository;
import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistRepository;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.BoothDetailResponseDto;
import org.example.reservationservice.reservation.infrastructure.festival.dto.StoreBoothOwnerResponseDto;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BoothWaitlistAcceptanceTest {

    private static final Long FESTIVAL_ID = 100L;
    private static final Long BOOTH_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long STOREHOST_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BoothWaitlistRepository boothWaitlistRepository;

    @Autowired
    private BoothWaitlistCounterRepository boothWaitlistCounterRepository;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        boothWaitlistRepository.deleteAll();
        boothWaitlistCounterRepository.deleteAll();
        reservationRepository.deleteAll();
        reset(festivalServiceClient);
        when(festivalServiceClient.getBooth(anyLong())).thenReturn(new BoothDetailResponseDto(BOOTH_ID, FESTIVAL_ID, "OPEN"));
        when(festivalServiceClient.getMyBooth(anyLong(), anyLong(), anyString()))
                .thenReturn(new StoreBoothOwnerResponseDto(BOOTH_ID, FESTIVAL_ID, STOREHOST_ID));
    }

    private void confirmedTicketFor(Long userId) {
        reservationRepository.save(Reservation.builder()
                .userId(userId)
                .festivalId(FESTIVAL_ID)
                .ticketTypeId(1L)
                .quantity(1)
                .price(10000)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .build());
    }

    @Test
    void ticketHolderCanRequestWaitlistAndGetsQueueNumberOne() throws Exception {
        confirmedTicketFor(USER_ID);

        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.queueNumber", org.hamcrest.Matchers.is(1)));
    }

    @Test
    void queueNumbersAreSequentialAcrossDifferentUsers() throws Exception {
        confirmedTicketFor(1L);
        confirmedTicketFor(2L);

        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", 1L))
                .andExpect(jsonPath("$.data.queueNumber", org.hamcrest.Matchers.is(1)));
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", 2L))
                .andExpect(jsonPath("$.data.queueNumber", org.hamcrest.Matchers.is(2)));
    }

    @Test
    void duplicateRequestFromSameUserIsRejected() throws Exception {
        confirmedTicketFor(USER_ID);

        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("ALREADY_REQUESTED")));
    }

    @Test
    void userWithoutTicketForFestivalIsRejected() throws Exception {
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("TICKET_NOT_FOUND")));
    }

    @Test
    void closedBoothRejectsWaitlistRequest() throws Exception {
        confirmedTicketFor(USER_ID);
        when(festivalServiceClient.getBooth(anyLong())).thenReturn(new BoothDetailResponseDto(BOOTH_ID, FESTIVAL_ID, "CLOSED"));

        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("BOOTH_NOT_OPEN")));
    }

    @Test
    void myWaitlistReturnsQueueNumber() throws Exception {
        confirmedTicketFor(USER_ID);
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID));

        mockMvc.perform(get("/api/booth-waitlists/" + BOOTH_ID + "/me").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queueNumber", org.hamcrest.Matchers.is(1)));
    }

    @Test
    void storehostCanCallNextAndQueueStatusReflectsIt() throws Exception {
        confirmedTicketFor(1L);
        confirmedTicketFor(2L);
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", 1L));
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", 2L));

        mockMvc.perform(post("/api/booth-waitlists/booths/" + BOOTH_ID + "/call-next")
                        .header("X-User-Id", STOREHOST_ID)
                        .header("X-User-Role", "STOREHOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calledNumber", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.data.waitingCount", org.hamcrest.Matchers.is(1)));

        mockMvc.perform(get("/api/booth-waitlists/booths/" + BOOTH_ID + "/queue-status")
                        .header("X-User-Id", STOREHOST_ID)
                        .header("X-User-Role", "STOREHOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calledNumber", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.data.issuedNumber", org.hamcrest.Matchers.is(2)));
    }

    @Test
    void callNextFailsWhenNoOneIsWaiting() throws Exception {
        mockMvc.perform(post("/api/booth-waitlists/booths/" + BOOTH_ID + "/call-next")
                        .header("X-User-Id", STOREHOST_ID)
                        .header("X-User-Role", "STOREHOST"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("NO_WAITING_QUEUE")));
    }

    @Test
    void nonOwnerCannotCallNext() throws Exception {
        confirmedTicketFor(1L);
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", 1L));

        mockMvc.perform(post("/api/booth-waitlists/booths/" + BOOTH_ID + "/call-next")
                        .header("X-User-Id", 999L)
                        .header("X-User-Role", "STOREHOST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", org.hamcrest.Matchers.is("FORBIDDEN_NOT_OWNER")));
    }

    @Test
    void myActiveWaitlistsReportsMyTurnAfterCallNext() throws Exception {
        confirmedTicketFor(USER_ID);
        mockMvc.perform(post("/api/booth-waitlists/" + BOOTH_ID).header("X-User-Id", USER_ID));

        mockMvc.perform(get("/api/booth-waitlists/me/active").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myTurn", org.hamcrest.Matchers.is(false)));

        mockMvc.perform(post("/api/booth-waitlists/booths/" + BOOTH_ID + "/call-next")
                        .header("X-User-Id", STOREHOST_ID)
                        .header("X-User-Role", "STOREHOST"));

        mockMvc.perform(get("/api/booth-waitlists/me/active").header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].myTurn", org.hamcrest.Matchers.is(true)));
    }
}
