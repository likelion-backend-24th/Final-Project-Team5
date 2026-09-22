package org.example.reservationservice.boothwaitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.example.reservationservice.boothwaitlist.exception.BoothWaitlistErrorCode;
import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistCounterRepository;
import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistRepository;
import org.example.reservationservice.boothwaitlist.service.BoothWaitlistService;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.BoothDetailResponseDto;
import org.example.reservationservice.reservation.infrastructure.festival.dto.StoreBoothOwnerResponseDto;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** 갱신한 카운터가 없으면 일반 서버 오류 대신 업무 충돌로 응답한다. */
@ExtendWith(MockitoExtension.class)
class BoothWaitlistCounterTest {

    @Mock BoothWaitlistCounterRepository counters;
    @Mock BoothWaitlistRepository waitlists;
    @Mock ReservationRepository reservations;
    @Mock FestivalServiceClient festivals;
    @InjectMocks BoothWaitlistService service;

    @Test
    void 대기_신청_후_카운터가_없으면_충돌로_안내한다() {
        when(festivals.getBooth(1L)).thenReturn(new BoothDetailResponseDto(1L, 2L, "OPEN"));
        when(reservations.existsByUserIdAndFestivalIdAndReservationStatusIn(eq(3L), eq(2L), any())).thenReturn(true);

        assertThatThrownBy(() -> service.requestWaitlist(3L, 1L)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", BoothWaitlistErrorCode.COUNTER_CONFLICT);
        assertThat(BoothWaitlistErrorCode.COUNTER_CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 순번_호출_후_카운터가_없으면_충돌로_안내한다() {
        when(festivals.getMyBooth(1L, 3L, "STOREHOST")).thenReturn(new StoreBoothOwnerResponseDto(1L, 2L, 3L));
        when(counters.callNext(1L)).thenReturn(1);

        assertThatThrownBy(() -> service.callNext(3L, "STOREHOST", 1L)).isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", BoothWaitlistErrorCode.COUNTER_CONFLICT);
    }
}
