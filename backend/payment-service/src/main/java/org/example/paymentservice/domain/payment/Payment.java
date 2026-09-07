package org.example.paymentservice.domain.payment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 예매(Reservation) 1건에 대응하는 결제 건. PortOne의 paymentId를 그대로 식별자로 사용한다.
 * 실제 승인 시도(재시도 포함)는 {@link PaymentTransaction}에 별도로 기록한다.
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payments_payment_id", columnNames = "payment_id"))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // PortOne API·SDK와 공유하는 팀 접두사 포함 결제 ID (예: BE24-T05-...)
    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    // Reservation-Service 소유 예매 ID. 서비스 간 FK가 아니라 참조값으로만 저장한다.
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    // 티켓 원가 스냅샷. 예매 시점 가격을 기준으로 하며, 이후 가격이 바뀌어도 영향받지 않는다.
    @Column(name = "ticket_amount", nullable = false)
    private long ticketAmount;

    // 플랫폼 수수료 스냅샷. 수수료율 확정 전까지는 0으로 둔다.
    @Column(name = "platform_fee", nullable = false)
    private long platformFee;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "pay_method", length = 30)
    private String payMethod;

    // MySQL 네이티브 ENUM으로 만들면 값 추가 시 ddl-auto: update가 허용값을 넓혀주지 않으므로 VARCHAR로 고정한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(30)")
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long totalAmount() {
        return ticketAmount + platformFee;
    }

    // 허용되지 않은 상태 전이는 조용히 무시하지 않고 예외로 드러낸다.
    public void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }
}
