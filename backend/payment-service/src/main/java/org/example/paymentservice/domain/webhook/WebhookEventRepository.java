package org.example.paymentservice.domain.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByWebhookId(String webhookId);

    // 재시도 대상: 일시 장애로 실패한 이벤트와, 저장 후 처리 도중 서버가 멈춰 RECEIVED로 오래 남은 이벤트.
    @Query("select w from WebhookEvent w where w.retryCount < :maxRetryCount"
            + " and (w.status = :failed or (w.status = :received and w.createdAt < :receivedBefore))"
            + " order by w.id")
    List<WebhookEvent> findRetryTargets(@Param("maxRetryCount") int maxRetryCount,
                                        @Param("failed") WebhookEventStatus failed,
                                        @Param("received") WebhookEventStatus received,
                                        @Param("receivedBefore") LocalDateTime receivedBefore);
}
