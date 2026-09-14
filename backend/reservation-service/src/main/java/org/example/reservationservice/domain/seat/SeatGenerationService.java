package org.example.reservationservice.domain.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatGenerationService {

    private final SeatRepository seatRepository;

    //festival-service가 페스티벌 승인 시 호출한다. rows × seatsPerRow개의 좌석을 벌크 생성한다.
    //같은 ticketTypeId로 이미 좌석이 생성돼 있으면 재생성하지 않고 멱등하게 넘어간다
    //(FestivalPublishRetryScheduler가 재시도할 때 중복 생성되지 않도록).
    @Transactional
    public void generateSeats(SeatGenerationRequestDto request) {
        if (seatRepository.existsByTicketTypeId(request.ticketTypeId())) {
            return;
        }

        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= request.rows(); row++) {
            for (int number = 1; number <= request.seatsPerRow(); number++) {
                seats.add(Seat.builder()
                        .festivalId(request.festivalId())
                        .ticketTypeId(request.ticketTypeId())
                        .zone(request.zone())
                        .rowLabel(row + "열")
                        .seatNumber(number)
                        .seatStatus(SeatStatus.AVAILABLE)
                        .build());
            }
        }
        seatRepository.saveAll(seats);
    }

    //참가자용 좌석맵 조회 — 인증 불필요(페스티벌 목록/상세 조회와 같은 공개 정책)
    public List<SeatResponseDto> listSeats(Long festivalId, Long ticketTypeId) {
        return seatRepository.findByFestivalIdAndTicketTypeIdOrderByZoneAscRowLabelAscSeatNumberAsc(festivalId, ticketTypeId)
                .stream()
                .map(SeatResponseDto::from)
                .toList();
    }
}