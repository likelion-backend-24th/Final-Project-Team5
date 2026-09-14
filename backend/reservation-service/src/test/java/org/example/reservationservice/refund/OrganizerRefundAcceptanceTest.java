package org.example.reservationservice.refund;
import org.example.reservationservice.reservation.entity.*;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueueRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;

@SpringBootTest @AutoConfigureMockMvc
class OrganizerRefundAcceptanceTest {
    @Autowired ReservationRepository reservations;
    @Autowired StockReleaseQueueRepository stock;
    @Autowired MockMvc mvc;
    @Test void checkedInTicketsAreFullyRefundableAndPartialCallbackIsIdempotent() throws Exception {
        var r = reservations.save(Reservation.builder().userId(1L).festivalId(42L).hostUserId(10L).ticketTypeId(5L)
                .price(10000).quantity(3).reservationStatus(ReservationStatus.CONFIRMED).paymentId("organizer-p")
                .checkedInAt(Instant.now()).build());
        mvc.perform(get("/internal/v1/reservations/" + r.getId() + "/organizer-refund-quote")
                        .header("Authorization", "Bearer test-internal-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refundAmount").value(30000))
                .andExpect(jsonPath("$.feePercent").value(0));
        for (int i = 0; i < 2; i++) mvc.perform(patch("/internal/v1/reservations/" + r.getId() + "/refund")
                        .header("Authorization", "Bearer test-internal-token").contentType("application/json")
                        .content("{\"paymentId\":\"organizer-p\",\"quantity\":1,\"cancellationId\":\"receipt-" + r.getId() + "\"}"))
                .andExpect(status().isOk());
        assertThat(reservations.findById(r.getId()).orElseThrow().getRefundedQuantity()).isEqualTo(1);
        assertThat(stock.findAll().stream().filter(q -> q.getReservationId().equals(r.getId())).count()).isEqualTo(1);
        mvc.perform(get("/internal/v1/reservations/" + r.getId()).header("Authorization", "Bearer test-internal-token"))
                .andExpect(jsonPath("$.festivalId").value(42)).andExpect(jsonPath("$.hostUserId").value(10))
                .andExpect(jsonPath("$.unitPrice").value(10000)).andExpect(jsonPath("$.refundedQuantity").value(1));
    }
}
