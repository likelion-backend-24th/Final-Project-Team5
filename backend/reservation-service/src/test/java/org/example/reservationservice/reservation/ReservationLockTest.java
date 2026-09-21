package org.example.reservationservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.reservationservice.reservation.dto.ReservationConfirmRequestDto;
import org.example.reservationservice.reservation.entity.CancelReason;
import org.example.reservationservice.reservation.entity.CheckInCodeGenerator;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.reservation.scheduler.ReservationExpiryScheduler;
import org.example.reservationservice.reservation.service.ReservationService;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.example.reservationservice.seat.service.SeatBroadcastService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 확정과 만료가 잠금 조회 뒤의 상태를 사용하고 기존 만료 정책을 유지하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class ReservationLockTest {

    @Mock ReservationRepository repository;
    @Mock FestivalServiceClient festivals;
    @Mock ReservationSeatRepository reservationSeats;
    @Mock SeatRepository seats;
    @Mock SeatBroadcastService broadcast;
    @Mock CheckInCodeGenerator codes;
    @InjectMocks ReservationService service;
    @InjectMocks ReservationExpiryScheduler scheduler;

    @Test
    void 확정은_잠금으로_조회하고_아직_취소되지_않은_만료_후_결제도_허용한다() {
        Reservation reservation = pending();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));
        when(codes.generate()).thenReturn("AA-BBBB-CCCC");

        service.confirmReservation(1L, new ReservationConfirmRequestDto("payment", 1000, "CARD", Instant.now()));

        verify(repository).findByIdForUpdate(1L);
        verify(repository, never()).findById(1L);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void 만료_후보라도_잠금_전에_확정되었으면_재고를_복구하지_않는다() {
        Reservation reservation = pending();
        reservation.confirm("payment", "AA-BBBB-CCCC");
        candidates(reservation);

        scheduler.expireStaleReservations();

        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(festivals, never()).restoreStock(2L, 1);
    }

    @Test
    void 잠금_전에_홀드가_연장된_후보는_취소하지_않는다() {
        Reservation reservation = pending();
        reservation.extendHold(Instant.now().plusSeconds(3600));
        candidates(reservation);

        scheduler.expireStaleReservations();

        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(festivals, never()).restoreStock(2L, 1);
    }

    @Test
    void 잠금_후에도_만료된_PENDING이면_취소하고_재고를_복구한다() {
        Reservation reservation = pending();
        candidates(reservation);

        scheduler.expireStaleReservations();

        verify(repository).findByIdForUpdate(1L);
        assertThat(reservation.getCancelReason()).isEqualTo(CancelReason.EXPIRED);
        verify(festivals).restoreStock(2L, 1);
    }

    private void candidates(Reservation reservation) {
        when(repository.findIdsByReservationStatusAndExpiresAtBefore(eq(ReservationStatus.PENDING), any()))
                .thenReturn(List.of(1L));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(reservation));
    }

    private Reservation pending() {
        return Reservation.builder().id(1L).ticketTypeId(2L).quantity(1).price(1000)
                .reservationStatus(ReservationStatus.PENDING).expiresAt(Instant.now().minusSeconds(60)).build();
    }
}
