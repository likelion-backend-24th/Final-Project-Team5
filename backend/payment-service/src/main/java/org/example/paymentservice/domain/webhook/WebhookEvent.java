package org.example.paymentservice.domain.webhook;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 수신한 웹훅의 중복 방지 기록. webhook_id 유니크 제약으로 같은 웹훅이 재전송돼도 한 번만 처리한다
 * (실전 가이드 8.5). Payload 원문은 저장하지 않는다 — 필요한 값(paymentId 등)만 뽑아서 남긴다.
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
