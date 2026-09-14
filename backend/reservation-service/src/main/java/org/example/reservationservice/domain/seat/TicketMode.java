package org.example.reservationservice.domain.seat;

/**
 * festival-service의 TicketMode(domain.tickettype.TicketMode)와 값이 정확히 일치해야 한다.
 * 서로 다른 서비스라 클래스를 공유할 수 없어 각자 독립적으로 갖고 있다 —
 * festival-service 쪽에 값이 추가/변경되면 이쪽도 수동으로 맞춰야 한다.
 */
public enum TicketMode {
    SEATED,
    STANDING
}