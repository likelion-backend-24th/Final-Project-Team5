package org.example.reservationservice.boothwaitlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 부스 선착순 대기 신청 1건. 같은 사용자가 같은 부스에 두 번 신청할 수 없도록 (booth_id, user_id) 유니크
 * 제약을 건다 — 서비스 계층에서도 미리 확인하지만, 동시 요청까지 막는 최종 방어선은 이 제약이다.
 */
@Entity
@Table(name = "booth_waitlists", uniqueConstraints = @UniqueConstraint(columnNames = {"booth_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoothWaitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    //조회 편의용 스냅샷(festival-service를 다시 조회하지 않고도 어느 페스티벌 소속인지 알 수 있게)
    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "queue_number", nullable = false)
    private int queueNumber;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    public BoothWaitlist(Long boothId, Long festivalId, Long userId, int queueNumber) {
        this.boothId = boothId;
        this.festivalId = festivalId;
        this.userId = userId;
        this.queueNumber = queueNumber;
    }
}
