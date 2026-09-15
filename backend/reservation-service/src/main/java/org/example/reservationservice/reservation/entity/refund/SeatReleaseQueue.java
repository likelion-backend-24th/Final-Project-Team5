package org.example.reservationservice.reservation.entity.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 환불된 특정 좌석을 바로 풀지 않고 모아 두었다가 정해진 시각(기본 매일 19:00)에 일괄로 AVAILABLE로
 * 되돌리기 위한 대기열. StockReleaseQueue의 좌석 버전 — ticketTypeId+quantity 대신 seatId 하나를 담는다.
 * 환불→즉시 재판매를 노리는 리셀을 막기 위한 팀 정책이다. 재고 복구가 실패하면 releasedAt이 비어 있어
 * 다음 회차에 다시 시도된다(좌석 유실 방지).
 */
@Entity
@Table(name = "seat_release_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatReleaseQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    //이 시각이 지나면 좌석을 AVAILABLE로 되돌린다(UTC 기준 절대 시각)
    @Column(name = "release_at", nullable = false)
    private Instant releaseAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SeatReleaseQueue(Long reservationId, Long seatId, Instant releaseAt) {
        this.reservationId = reservationId;
        this.seatId = seatId;
        this.releaseAt = releaseAt;
    }

    public void markReleased(Instant at) {
        this.releasedAt = at;
    }
}
