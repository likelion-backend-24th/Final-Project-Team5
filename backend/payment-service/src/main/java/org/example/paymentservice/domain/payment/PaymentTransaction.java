package org.example.paymentservice.domain.payment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 같은 paymentId에서 발생한 개별 승인 시도. 실패 후 재시도하면 같은 Payment에
 * 여러 PaymentTransaction이 쌓인다 — PortOne 조회 결과의 transactionId별 스냅샷.
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "payment_transactions", uniqueConstraints = @UniqueConstraint(name = "uk_payment_transactions_transaction_id", columnNames = "transaction_id"))
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // PortOne이 이 승인 시도에 부여한 거래 ID
    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(30)")
    private PaymentStatus status;

    // PortOne 조회 응답의 결제금액 스냅샷 (내부 Payment 금액과 대조 검증용)
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "pay_method", length = 30)
    private String payMethod;

    // FAILED일 때만 채워지는 실패 사유. 카드번호·CVC 등 민감정보는 저장하지 않는다.
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
