package org.example.paymentservice.domain.cancellation;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.paymentservice.domain.payment.Payment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 한 건의 취소(전체 또는 부분). 같은 Payment에 부분 취소가 여러 번 쌓일 수 있어 별도 테이블로 둔다.
 *
 * PortOne이 부여하는 cancellationId가 이 행의 자연키지만, 우리가 취소를 "요청"한 직후에는 아직
 * 그 값을 모른다(가이드 9.1: REQUESTED 저장 → PortOne 호출). 그래서 nullable로 두고 응답·웹훅
 * 재조회 때 채운다.
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(
        name = "cancellations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cancellations_cancellation_id", columnNames = "cancellation_id"),
                @UniqueConstraint(name = "uk_cancellations_idempotency_key", columnNames = "idempotency_key")
        }
)
public class Cancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** PortOne이 부여한 취소 ID. 요청 직후에는 아직 없을 수 있다. */
    @Column(name = "cancellation_id", length = 100)
    private String cancellationId;

    /**
     * 같은 논리 요청의 재시도를 한 건으로 묶는 키(가이드 5.4). 사용자가 헤더로 보낸 값을 그대로 쓴다.
     * 외부에서 발견한 취소(WEBHOOK_DISCOVERED)에는 없다.
     */
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private CancellationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, columnDefinition = "VARCHAR(30)")
    private CancellationSource source;

    /** 취소 요청 금액. */
    @Column(name = "amount", nullable = false)
    private long amount;

    /** 환불된 티켓 장수. 예매 쪽 재고를 되돌릴 단위라서 금액과 별도로 들고 있는다. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * PortOne 재조회 결과로 이 취소를 최신 상태에 맞춘다.
     * 가이드 9.5 — 이미 확정된 취소를 늦게 도착한 중간 상태가 되돌리지 않게 막는다.
     */
    public void syncFrom(String cancellationId, CancellationStatus remoteStatus, Instant cancelledAt) {
        if (this.cancellationId == null) {
            this.cancellationId = cancellationId;
        }
        if (this.status.isFinal()) {
            return;
        }
        this.status = remoteStatus;
        this.cancelledAt = cancelledAt;
    }

    public void markFailed() {
        if (!this.status.isFinal()) {
            this.status = CancellationStatus.FAILED;
        }
    }
}
