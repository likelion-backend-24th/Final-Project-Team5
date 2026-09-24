package org.example.paymentservice.domain.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일시 장애로 처리하지 못한 웹훅의 재시도 배치(실전 가이드 8.6 권장 구현의 Worker, 12.6 복구 작업).
 * 저장된 웹훅 ID를 기준으로 다시 처리하므로 PortOne이 재전송하지 않아도 결제 동기화가 결국 끝난다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "webhook-retry.scheduling-enabled", matchIfMissing = true)
public class WebhookRetryScheduler {

    private final WebhookEventService webhookEventService;

    @Scheduled(fixedDelayString = "${webhook-retry.delay-ms:60000}")
    public void run() {
        try {
            webhookEventService.retryPending();
        } catch (RuntimeException e) {
            // 재시도 대상 조회·저장 자체가 실패해도 다음 주기에 같은 이벤트를 다시 집는다.
            log.warn("웹훅 재시도 배치 실패 — 다음 주기에 다시 시도한다.", e);
        }
    }
}
