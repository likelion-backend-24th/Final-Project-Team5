package org.example.paymentservice.domain.webhook;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * PortOne 웹훅 수신 전용. 사용자 인증(X-User-Id) 대신 PortOne 서명으로 인증하므로
 * Gateway에서 공개 라우트로 예외 처리해야 한다(Task 7-6).
 */
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookVerifier webhookVerifier;
    private final WebhookEventService webhookEventService;

    // 역직렬화 전 Raw Body를 그대로 서명 검증에 넘긴다(String 파라미터라 JSON 파싱을 거치지 않는다).
    @PostMapping("/api/v1/webhooks/portone")
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSignature,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTimestamp) {
        Webhook webhook;
        try {
            webhook = webhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp);
        } catch (WebhookVerificationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        webhookEventService.process(webhookId, webhook);
        return ResponseEntity.ok().build();
    }
}
