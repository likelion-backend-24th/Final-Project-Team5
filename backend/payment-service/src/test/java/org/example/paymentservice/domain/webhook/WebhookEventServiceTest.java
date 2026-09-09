package org.example.paymentservice.domain.webhook;

import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.PaymentCancellationService;
import org.example.paymentservice.domain.payment.PaymentErrorCode;
import org.example.paymentservice.domain.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebhookTransactionPaid 등 실제 이벤트 구현체는 Kotlin SDK 안에서 internal 생성자로 막혀 있어
 * Java 테스트에서 직접 생성할 수 없다. WebhookTransaction/WebhookTransactionData는 인터페이스라
 * Mockito로 흉내내 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WebhookEventServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentCancellationService paymentCancellationService;

    private WebhookEventService webhookEventService;

    @BeforeEach
    void setUp() {
        webhookEventService =
                new WebhookEventService(webhookEventRepository, paymentService, paymentCancellationService);
    }

    private WebhookTransaction transactionWebhook(String paymentId) {
        WebhookTransactionData data = org.mockito.Mockito.mock(WebhookTransactionData.class);
        lenient().when(data.getPaymentId()).thenReturn(paymentId);
        WebhookTransaction webhook = org.mockito.Mockito.mock(WebhookTransaction.class);
        lenient().when(webhook.getData()).thenReturn(data);
        return webhook;
    }

    @Test
    void 신규_웹훅은_저장하고_결제_동기화를_호출한다() {
        WebhookTransaction webhook = transactionWebhook("BE24-T05-1");

        webhookEventService.process("webhook-1", webhook);

        verify(webhookEventRepository).saveAndFlush(any(WebhookEvent.class));
        verify(paymentService).syncPayment("BE24-T05-1");
    }

    @Test
    void 중복_웹훅은_동기화를_호출하지_않는다() {
        WebhookTransaction webhook = transactionWebhook("BE24-T05-1");
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        webhookEventService.process("webhook-1", webhook);

        verify(paymentService, never()).syncPayment(anyString());
    }

    @Test
    void paymentId가_없는_이벤트는_안전하게_무시한다() {
        Webhook nonTransactionWebhook = org.mockito.Mockito.mock(Webhook.class);

        webhookEventService.process("webhook-2", nonTransactionWebhook);

        verify(webhookEventRepository).saveAndFlush(any(WebhookEvent.class));
        verify(paymentService, never()).syncPayment(anyString());
    }

    @Test
    void 동기화_실패는_예외를_전파하지_않는다() {
        WebhookTransaction webhook = transactionWebhook("BE24-T05-1");
        when(paymentService.syncPayment("BE24-T05-1"))
                .thenThrow(new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        webhookEventService.process("webhook-1", webhook);
        // 예외가 여기까지 전파되지 않고 끝나면 성공(항상 200을 응답할 수 있어야 한다)
    }
}
