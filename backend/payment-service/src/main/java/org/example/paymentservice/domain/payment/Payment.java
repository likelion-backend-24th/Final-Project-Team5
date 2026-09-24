package org.example.paymentservice.domain.payment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.paymentservice.domain.settlement.SettlementCalculator;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
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

    // 정산 확정 시 결제가 그 사이 바뀌지 않았는지 대조하는 낙관적 락 버전. 기존 행은 0으로 시작한다.
    @Version
    @Column(columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    // PortOne API·SDK와 공유하는 팀 접두사 포함 결제 ID (예: BE24-T05-...)
    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    // Reservation-Service 소유 예매 ID. 서비스 간 FK가 아니라 참조값으로만 저장한다.
    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    // 결제 완료 API 호출 시 본인 소유 확인용. 예매 조회 없이 바로 검증하기 위해 준비 시점에 저장한다.
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 티켓 원가 스냅샷. 예매 시점 가격을 기준으로 하며, 이후 가격이 바뀌어도 영향받지 않는다.
    @Column(name = "ticket_amount", nullable = false)
    private long ticketAmount;

    // 구매자가 추가 부담하지 않는 주최자 정산 공제액.
    @Column(name = "platform_fee", nullable = false)
    private long platformFee;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "pay_method", length = 30)
    private String payMethod;

    // 정산 계산이 예매·페스티벌 서비스를 다시 조회하지 않도록 승인 시점에 복사해 두는 스냅샷.
    private Long festivalId;
    private Long hostUserId;
    private Long unitPrice;
    private Instant paidAt;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private PaymentMethodCategory payMethodCategory;
    private String easyPayProvider;
    private Integer platformFeeRateBps;
    private String feePolicyVersion;
    private Boolean testPayment;
    // 예매 확정 호출이 성공한 시각. null이면 결제는 PAID인데 예매 확정이 안 된 상태라 완료 API 재호출 때 다시 시도한다.
    private Instant reservationConfirmedAt;
    // 가상계좌가 처음 발급된 시각. 데모 자동 입금은 이 시각을 기준으로 지연을 계산한다(재조회로 다시 발급 상태가 와도 유지).
    private Instant virtualAccountIssuedAt;
    // 데모 자동 입금이 처리된 시각. null이 아니면 PortOne 실제 상태 대신 로컬 입금 기록을 원격 상태로 간주한다.
    private Instant demoDepositedAt;
    // 결제는 승인됐는데 예매 확정이 거절된 시각(예매가 이미 다른 결제로 확정됐거나 만료·취소됨). 이 결제로는 티켓을 줄 수 없어
    // 자동 전액 환불(보상) 대상이고, 예매에 반영된 매출이 아니므로 정산·행사 취소 환불에서 제외한다(실전 가이드 7.4·11.4).
    private Instant reservationRejectedAt;

    public void markReservationConfirmed() {
        reservationConfirmedAt = Instant.now();
    }

    public void markReservationRejected() {
        if (reservationRejectedAt == null) {
            reservationRejectedAt = Instant.now();
        }
    }

    public boolean isReservationRejected() {
        return reservationRejectedAt != null;
    }

    public void markVirtualAccountIssued() {
        if (virtualAccountIssuedAt == null) {
            virtualAccountIssuedAt = Instant.now();
        }
    }

    public void markDemoDeposited() {
        demoDepositedAt = Instant.now();
    }

    public boolean isDemoDeposited() {
        return demoDepositedAt != null;
    }

    // PortOne 승인 응답에서 결제수단·승인 시각·채널을 복사하고 수수료율을 확정한다. 이후 정산은 이 스냅샷만 본다.
    public void snapshotApproval(String rawMethod, String provider, Instant approvedAt, Boolean test) {
        this.payMethod = rawMethod;
        this.payMethodCategory = PaymentMethodCategory.fromRaw(rawMethod);
        this.easyPayProvider = provider;
        this.paidAt = approvedAt;
        this.testPayment = test;
        this.platformFeeRateBps = payMethodCategory.rateBps();
        this.feePolicyVersion = "2026-09-v1";
        this.platformFee = platformFeeRateBps == null ? 0 : SettlementCalculator.fee(ticketAmount, platformFeeRateBps);
    }

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

    // 구매자 결제액. platformFee는 주최자 정산에서 공제하는 금액이라 여기에 더하지 않는다.
    public long totalAmount() {
        return ticketAmount;
    }

    // 허용되지 않은 상태 전이는 조용히 무시하지 않고 예외로 드러낸다.
    public void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않은 상태 전이: " + status + " -> " + next);
        }
        this.status = next;
    }
}
