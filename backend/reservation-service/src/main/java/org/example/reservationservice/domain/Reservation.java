package org.example.reservationservice.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Entity
@Table(name = "reservations")
public class Reservation {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ticket_type_id")
    private Long ticketTypeId;

    private int quantity;        // 몇 장을 예매 하는지
    private int price;       // 예매 시점 가격 스냅샷 (나중에 가격이 바뀌어도 예매 내역은 고정)

    //결제 확정 시 Payment-Service가 보내는 paymentId. confirm 재호출 시 같은 값이면 멱등 처리(200)한다.
    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    //columnDefinition을 명시하지 않으면 Hibernate가 MySQL 네이티브 ENUM(...) 컬럼을 생성해,
    //Java enum에 값을 추가해도 ddl-auto: update가 DB의 허용값 목록을 넓혀주지 않는다.
    @Column(name = "reservation_status", columnDefinition = "VARCHAR(20)")
    @Enumerated(EnumType.STRING)
    private ReservationStatus reservationStatus;

    //CANCELLED일 때만 채워진다.
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", columnDefinition = "VARCHAR(20)")
    private CancelReason cancelReason;

    //PENDING 상태가 결제 없이 이 시각을 넘기면 만료 배치가 CANCELLED(EXPIRED)로 전환하고 재고를 복구한다.
    //Payment-Service의 ReservationForPaymentResponse.expiresAt이 Instant(UTC, "...Z")를 기대하므로 반드시 Instant로 유지한다.
    @Column(name = "expires_at")
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long totalAmount() {
        return (long) price * quantity;
    }

    //결제 성공 확정
    public void confirm(String paymentId) {
        this.reservationStatus = ReservationStatus.CONFIRMED;
        this.paymentId = paymentId;
    }

    //결제 실패·취소·만료
    public void cancel(CancelReason reason) {
        this.reservationStatus = ReservationStatus.CANCELLED;
        this.cancelReason = reason;
    }
}
