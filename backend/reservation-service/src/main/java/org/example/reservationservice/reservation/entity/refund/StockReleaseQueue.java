package org.example.reservationservice.domain.refund;

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
 * 환불로 돌아온 재고를 바로 풀지 않고 모아 두었다가 정해진 시각(기본 매일 19:00)에 일괄로 되돌리기 위한 대기열.
 * 환불→즉시 재판매를 노리는 리셀을 막기 위한 팀 정책이다. 재고 복구 호출이 실패하면 releasedAt이 비어 있어
 * 다음 회차에 다시 시도된다(재고 유실 방지).
 */
@Entity
@Table(name = "stock_release_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReleaseQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    @Column(nullable = false)
    private int quantity;

    //이 시각이 지나면 재고를 되돌린다(UTC 기준 절대 시각)
    @Column(name = "release_at", nullable = false)
    private Instant releaseAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StockReleaseQueue(Long reservationId, Long ticketTypeId, int quantity, Instant releaseAt) {
        this.reservationId = reservationId;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.releaseAt = releaseAt;
    }

    public void markReleased(Instant at) {
        this.releasedAt = at;
    }
}
