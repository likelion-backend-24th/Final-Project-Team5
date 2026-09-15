package org.example.reservationservice.domain.seat;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //festival-service 소유 데이터 — 크로스 서비스 참조라 FK 없이 값만 저장한다
    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    //festival-service의 TicketType.id — 좌석 생성 시 festival-service가 알려준 배치 정보의 출처
    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    //구역명(예: "VIP", "일반")
    @Column(name = "zone", nullable = false)
    private String zone;

    //행(row) 라벨 — 좌석 배치도 표시용. 예: "3열"
    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    //같은 행 안에서의 좌석 번호
    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    //MySQL 8 예약어(ROWS) 충돌 회피와 같은 이유로, enum 컬럼은 항상 columnDefinition을 명시한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", columnDefinition = "VARCHAR(20)", nullable = false)
    private SeatStatus seatStatus;

    //선점(HELD) 중인 사용자 — AVAILABLE/SOLD일 때는 null
    @Column(name = "held_by")
    private Long heldBy;

    //이 시각이 지나면 만료 배치가 AVAILABLE로 되돌린다. HELD가 아니면 null
    //(Reservation.expiresAt과 같은 패턴 — DB 필드 + 스케줄러, Redis 미사용)
    @Column(name = "held_until")
    private Instant heldUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    //선점 확정(내부 메서드에서 직접 호출하지 않고, 조건부 UPDATE 쿼리가 이 역할을 대신한다 — Task 5에서 사용)
    //엔티티의 상태 전이 메서드는 조회 후 저장하는 일반 흐름(예: 만료 배치)에서만 쓴다.
    public void release() {
        this.seatStatus = SeatStatus.AVAILABLE;
        this.heldBy = null;
        this.heldUntil = null;
    }

    public void markSold() {
        this.seatStatus = SeatStatus.SOLD;
        this.heldBy = null;
        this.heldUntil = null;
    }
}