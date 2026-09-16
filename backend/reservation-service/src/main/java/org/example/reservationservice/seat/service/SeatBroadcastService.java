package org.example.reservationservice.seat.service;

import lombok.RequiredArgsConstructor;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 좌석 상태 변화를 WebSocket으로 브로드캐스트하는 책임만 담당한다.
 * 좌석 선점 성공(hold), 만료 원복(release), 결제 확정(sold) 시점에서 호출된다.
 */
@Service
@RequiredArgsConstructor
public class SeatBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(Long festivalId, Long ticketTypeId, Long seatId, SeatStatus status) {
        String destination = "/topic/festivals/" + festivalId + "/ticket-types/" + ticketTypeId + "/seats";
        messagingTemplate.convertAndSend(destination, new SeatStatusMessage(seatId, status));
    }

    public record SeatStatusMessage(Long seatId, SeatStatus status) {
    }
}