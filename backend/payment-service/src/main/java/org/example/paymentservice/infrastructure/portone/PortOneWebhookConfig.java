package org.example.paymentservice.infrastructure.portone;

import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortOneWebhookConfig {

    @Bean
    public WebhookVerifier webhookVerifier(@Value("${portone.webhook-secret}") String webhookSecret) {
        return new WebhookVerifier(webhookSecret);
    }
}
