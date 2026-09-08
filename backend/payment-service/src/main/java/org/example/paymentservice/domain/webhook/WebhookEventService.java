package org.example.paymentservice.domain.webhook;

import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.PaymentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;

    // 서명 검증까지 통과한 웹훅을 처리한다. 항상 200으로 응답할 수 있도록, 이 메서드는 예외를 던지지 않는다
    // (동기화 실패는 로그로 남기고 무시 — 실전 가이드 8.6 "동시에 같은 웹훅이 들어와도 ... 계속 처리하지 마세요"와
    // "다른 팀의 paymentId인 이벤트는 재시도하지 않고 IGNORED로 기록"을 함께 만족한다).
    public void process(String webhookId, Webhook webhook) {
        String paymentId = extractPaymentId(webhook);
        String eventType = webhook.getClass().getSimpleName();

        WebhookEvent event = WebhookEvent.builder()
                .webhookId(webhookId)
                .eventType(eventType)
                .paymentId(paymentId)
                .build();
        try {
            webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            log.info("중복 웹훅 무시. webhookId={}", webhookId);
            return;
        }

        if (paymentId == null) {
            // Transaction 계열이 아니거나(BillingKey 등) SDK가 인식 못한 이벤트 — 안전하게 무시한다.
            log.info("처리 대상 없는 웹훅 무시. webhookId={}, eventType={}", webhookId, eventType);
            return;
        }

        try {
            paymentService.syncPayment(paymentId);
        } catch (ApiException e) {
            // 다른 팀의 paymentId, 이미 검증 실패한 결제 등 — 재시도 없이 로그만 남긴다.
            log.warn("웹훅 동기화 실패(무시). webhookId={}, paymentId={}, errorCode={}",
                    webhookId, paymentId, e.getErrorCode().name(), e);
        }
    }

    private String extractPaymentId(Webhook webhook) {
        if (webhook instanceof WebhookTransaction transaction) {
            return transaction.getData().getPaymentId();
        }
        return null;
    }
}
