package org.example.paymentservice.domain.webhook;

import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.PaymentCancellationService;
import org.example.paymentservice.domain.payment.PaymentErrorCode;
import org.example.paymentservice.domain.payment.PaymentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventService {

    // 재시도 배치가 1분마다 도므로 약 30분 — 배포 직후 서비스가 늦게 뜨는 시간을 넘길 만큼 다시 시도한다.
    static final int MAX_RETRY_COUNT = 30;
    // 저장 후 이 시간이 지나도 RECEIVED면 처리 도중 서버가 멈춘 것으로 보고 다시 처리한다.
    static final long RECEIVED_STUCK_MINUTES = 5;
    // 다시 처리하면 결과가 달라질 수 있는(일시적인) 결제 오류. 나머지 ApiException은 다시 해도 같은 결과다.
    private static final Set<PaymentErrorCode> RETRYABLE_ERROR_CODES =
            Set.of(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE, PaymentErrorCode.PAYMENT_NOT_YET_PROCESSED);

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentService paymentService;
    private final PaymentCancellationService paymentCancellationService;

    // 서명 검증까지 통과한 웹훅을 처리한다. 항상 200으로 응답할 수 있도록, 이 메서드는 예외를 던지지 않는다
    // (실전 가이드 8.6 "동시에 같은 웹훅이 들어와도 ... 계속 처리하지 마세요"와
    // "다른 팀의 paymentId인 이벤트는 재시도하지 않고 IGNORED로 기록"을 함께 만족한다).
    // 일시 장애로 처리하지 못한 이벤트는 FAILED로 남겨, 같은 웹훅의 재전송이나 재시도 배치가 다시 처리한다.
    public void process(String webhookId, Webhook webhook) {
        String paymentId = extractPaymentId(webhook);
        String eventType = webhook.getClass().getSimpleName();

        WebhookEvent event = WebhookEvent.builder()
                .webhookId(webhookId)
                .eventType(eventType)
                .paymentId(paymentId)
                .status(WebhookEventStatus.RECEIVED)
                .build();
        try {
            webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // 먼저 온 전달이 처리를 끝냈거나 처리 중이면 무시한다. 일시 장애로 실패했던 이벤트만 재전송을 계기로 다시 처리한다.
            WebhookEvent previous = webhookEventRepository.findByWebhookId(webhookId).orElse(null);
            if (previous == null || !previous.needsReprocessing()) {
                log.info("중복 웹훅 무시. webhookId={}", webhookId);
                return;
            }
            log.info("처리에 실패했던 웹훅 재전송 — 다시 처리한다. webhookId={}, retryCount={}",
                    webhookId, previous.getRetryCount());
            event = previous;
        }

        handle(event);
    }

    // 처리하지 못한 웹훅을 다시 처리한다(WebhookRetryScheduler가 주기적으로 호출, 가이드 12.6 "처리 실패 WebhookEvent 재시도").
    public void retryPending() {
        List<WebhookEvent> targets = webhookEventRepository.findRetryTargets(MAX_RETRY_COUNT,
                WebhookEventStatus.FAILED, WebhookEventStatus.RECEIVED,
                LocalDateTime.now().minusMinutes(RECEIVED_STUCK_MINUTES));
        for (WebhookEvent event : targets) {
            handle(event);
        }
    }

    private void handle(WebhookEvent event) {
        String webhookId = event.getWebhookId();
        String paymentId = event.getPaymentId();
        if (paymentId == null) {
            // Transaction 계열이 아니거나(BillingKey 등) SDK가 인식 못한 이벤트 — 안전하게 무시한다.
            log.info("처리 대상 없는 웹훅 무시. webhookId={}, eventType={}", webhookId, event.getEventType());
            event.markIgnored(null);
            webhookEventRepository.save(event);
            return;
        }

        String retryableFailure = null;
        String ignoredReason = null;
        try {
            paymentService.syncPayment(paymentId);
        } catch (ApiException e) {
            if (e.getErrorCode() instanceof PaymentErrorCode code && RETRYABLE_ERROR_CODES.contains(code)) {
                log.warn("웹훅 동기화 일시 실패(재시도). webhookId={}, paymentId={}, errorCode={}",
                        webhookId, paymentId, e.getErrorCode().name(), e);
                retryableFailure = e.getErrorCode().name();
            } else {
                // 다른 팀의 paymentId, 이미 검증 실패한 결제 등 — 다시 해도 결과가 같으므로 재시도 없이 기록만 남긴다.
                log.warn("웹훅 동기화 실패(무시). webhookId={}, paymentId={}, errorCode={}",
                        webhookId, paymentId, e.getErrorCode().name(), e);
                ignoredReason = e.getErrorCode().name();
            }
        } catch (HttpClientErrorException e) {
            // 내부 서비스의 4xx는 요청 자체가 거절된 것이라 다시 보내도 같은 응답이 온다.
            log.warn("웹훅 동기화 거절(무시). webhookId={}, paymentId={}, status={}",
                    webhookId, paymentId, e.getStatusCode().value(), e);
            ignoredReason = "HTTP_" + e.getStatusCode().value();
        } catch (RuntimeException e) {
            // PortOne·예매 서비스 타임아웃, 5xx, 동시 수정 충돌 등 — 잠시 뒤 다시 하면 성공할 수 있다.
            log.warn("웹훅 동기화 일시 실패(재시도). webhookId={}, paymentId={}", webhookId, paymentId, e);
            retryableFailure = e.getClass().getSimpleName();
        }

        // 취소 대사는 별도로 한 번 더 돈다. syncPayment는 이미 확정(PAID 등)된 결제를 그대로 반환하고
        // 끝내기 때문에, 결제 후에 도착하는 취소 웹훅을 그 경로로는 반영할 수 없다.
        // 이벤트 종류를 SDK 클래스명으로 구분하는 대신 항상 대사하는 이유는 가이드 9.4·9.5대로
        // "최종 상태는 PortOne 최신 조회 결과로 결정"하기 위해서다 — 취소가 없으면 아무 일도 하지 않는다.
        try {
            paymentCancellationService.reconcileByPaymentId(paymentId);
        } catch (RuntimeException e) {
            log.warn("웹훅 취소 대사 실패(재시도). webhookId={}, paymentId={}", webhookId, paymentId, e);
            if (retryableFailure == null) {
                retryableFailure = e.getClass().getSimpleName();
            }
        }

        if (retryableFailure != null) {
            event.markFailed(retryableFailure);
            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("웹훅 자동 재시도 한도 도달 — 결제 상태를 수동으로 확인해야 한다. webhookId={}, paymentId={}, lastError={}",
                        webhookId, paymentId, retryableFailure);
            }
        } else if (ignoredReason != null) {
            event.markIgnored(ignoredReason);
        } else {
            event.markProcessed();
        }
        webhookEventRepository.save(event);
    }

    private String extractPaymentId(Webhook webhook) {
        if (webhook instanceof WebhookTransaction transaction) {
            return transaction.getData().getPaymentId();
        }
        return null;
    }
}
