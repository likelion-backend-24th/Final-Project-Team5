package org.example.paymentservice.domain.webhook;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookVerifier webhookVerifier;

    @Mock
    private WebhookEventService webhookEventService;

    private WebhookController webhookController;

    @BeforeEach
    void setUp() {
        webhookController = new WebhookController(webhookVerifier, webhookEventService);
    }

    @Test
    void 서명_검증에_성공하면_200을_응답하고_이벤트를_처리한다() throws WebhookVerificationException {
        Webhook webhook = org.mockito.Mockito.mock(Webhook.class);
        when(webhookVerifier.verify("{}", "webhook-1", "v1,sig", "1700000000")).thenReturn(webhook);

        ResponseEntity<Void> response = webhookController.receive("{}", "webhook-1", "v1,sig", "1700000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookEventService).process("webhook-1", webhook);
    }

    @Test
    void 서명_검증에_실패하면_400을_응답하고_이벤트를_처리하지_않는다() throws WebhookVerificationException {
        when(webhookVerifier.verify(any(), any(), any(), any()))
                .thenThrow(new WebhookVerificationException("bad signature", null));

        ResponseEntity<Void> response = webhookController.receive("{}", "webhook-1", "v1,bad-sig", "1700000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(webhookEventService, never()).process(any(), any());
    }
}
