package org.example.paymentservice.domain.webhook;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 수신한 웹훅의 중복 방지·처리 상태 기록. webhook_id 유니크 제약으로 같은 웹훅이 재전송돼도 한 번만 처리한다
 * (실전 가이드 8.5). 다만 일시 장애로 처리하지 못한 웹훅은 재전송이나 재시도 배치가 다시 처리한다(8.6·12.6).
 * Payload 원문은 저장하지 않는다 — 필요한 값(paymentId 등)만 뽑아서 남긴다.
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(name = "uk_webhook_events_webhook_id", columnNames = "webhook_id"))
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "webhook_id", nullable = false, length = 100)
    private String webhookId;

    // PortOne 이벤트 타입(예: Transaction.Paid). 알 수 없는 이벤트는 SDK 클래스명("Unrecognized")을 그대로 기록한다.
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // Transaction 계열이 아닌 이벤트(BillingKey 등)는 없을 수 있다.
    @Column(name = "payment_id", length = 100)
    private String paymentId;

    // 이 컬럼이 생기기 전에 저장된 행은 null이다 — 당시에는 저장과 동시에 처리를 끝냈으므로 처리가 끝난 이벤트로 본다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "VARCHAR(20)")
    private WebhookEventStatus status;

    // 일시 장애로 처리하지 못한 횟수. 재시도 배치는 한도까지만 다시 시도한다.
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    // 마지막 실패·무시 사유(에러 코드나 예외 이름). 원문 메시지는 남기지 않는다(가이드 12.4).
    @Column(name = "last_error", length = 100)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 재전송이 왔을 때 다시 처리해야 하는 이벤트인지. 처리 중(RECEIVED)인 이벤트는 동시 처리를 피하려고 제외한다.
    public boolean needsReprocessing() {
        return status == WebhookEventStatus.FAILED;
    }

    public void markProcessed() {
        this.status = WebhookEventStatus.PROCESSED;
        this.lastError = null;
    }

    public void markIgnored(String reason) {
        this.status = WebhookEventStatus.IGNORED;
        this.lastError = reason;
    }

    public void markFailed(String reason) {
        this.status = WebhookEventStatus.FAILED;
        this.retryCount++;
        this.lastError = reason;
    }
}
