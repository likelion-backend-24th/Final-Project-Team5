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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

    @Test
    void 처리를_마친_웹훅은_PROCESSED로_남긴다() {
        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        WebhookEvent saved = lastSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(saved.getRetryCount()).isZero();
    }

    @Test
    void 일시_장애로_동기화에_실패하면_재시도_대상으로_남긴다() {
        when(paymentService.syncPayment("BE24-T05-1")).thenThrow(new ResourceAccessException("PortOne timeout"));

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        WebhookEvent saved = lastSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(1);
        assertThat(saved.getLastError()).isEqualTo("ResourceAccessException");
    }

    @Test
    void 예매_서비스_일시_장애도_재시도_대상으로_남긴다() {
        when(paymentService.syncPayment("BE24-T05-1"))
                .thenThrow(new ApiException(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE));

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        assertThat(lastSavedEvent().getStatus()).isEqualTo(WebhookEventStatus.FAILED);
    }

    @Test
    void 취소_대사가_일시_실패해도_재시도_대상으로_남긴다() {
        doThrow(new ResourceAccessException("PortOne timeout"))
                .when(paymentCancellationService).reconcileByPaymentId("BE24-T05-1");

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        assertThat(lastSavedEvent().getStatus()).isEqualTo(WebhookEventStatus.FAILED);
    }

    @Test
    void 다른_팀_결제처럼_다시_해도_같은_실패는_재시도하지_않는다() {
        when(paymentService.syncPayment("BE24-T05-1"))
                .thenThrow(new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        WebhookEvent saved = lastSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(saved.getLastError()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(saved.getRetryCount()).isZero();
    }

    @Test
    void 내부_서비스의_4xx_거절은_재시도하지_않는다() {
        when(paymentService.syncPayment("BE24-T05-1"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null));

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        WebhookEvent saved = lastSavedEvent();
        assertThat(saved.getStatus()).isEqualTo(WebhookEventStatus.IGNORED);
        assertThat(saved.getLastError()).isEqualTo("HTTP_409");
    }

    @Test
    void 처리에_실패했던_웹훅이_재전송되면_다시_처리한다() {
        WebhookEvent failed = storedEvent(WebhookEventStatus.FAILED);
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(webhookEventRepository.findByWebhookId("webhook-1")).thenReturn(Optional.of(failed));

        webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));

        verify(paymentService).syncPayment("BE24-T05-1");
        assertThat(failed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        verify(webhookEventRepository).save(failed);
    }

    @Test
    void 처리를_마쳤거나_처리_중인_웹훅의_재전송은_다시_처리하지_않는다() {
        when(webhookEventRepository.saveAndFlush(any(WebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        //상태 컬럼이 생기기 전에 저장된 이벤트(null)도 처리를 마친 것으로 본다.
        for (WebhookEventStatus status : new WebhookEventStatus[]{
                WebhookEventStatus.PROCESSED, WebhookEventStatus.IGNORED, WebhookEventStatus.RECEIVED, null}) {
            when(webhookEventRepository.findByWebhookId("webhook-1")).thenReturn(Optional.of(storedEvent(status)));

            webhookEventService.process("webhook-1", transactionWebhook("BE24-T05-1"));
        }

        verify(paymentService, never()).syncPayment(anyString());
    }

    @Test
    void 재시도_배치는_실패한_웹훅을_다시_처리해_완료로_바꾼다() {
        WebhookEvent failed = storedEvent(WebhookEventStatus.FAILED);
        when(webhookEventRepository.findRetryTargets(eq(WebhookEventService.MAX_RETRY_COUNT),
                eq(WebhookEventStatus.FAILED), eq(WebhookEventStatus.RECEIVED), any()))
                .thenReturn(List.of(failed));

        webhookEventService.retryPending();

        verify(paymentService).syncPayment("BE24-T05-1");
        assertThat(failed.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
    }

    @Test
    void 재시도에서도_실패하면_실패_횟수가_쌓인다() {
        WebhookEvent failed = storedEvent(WebhookEventStatus.FAILED);
        when(webhookEventRepository.findRetryTargets(eq(WebhookEventService.MAX_RETRY_COUNT),
                eq(WebhookEventStatus.FAILED), eq(WebhookEventStatus.RECEIVED), any()))
                .thenReturn(List.of(failed));
        when(paymentService.syncPayment("BE24-T05-1")).thenThrow(new ResourceAccessException("PortOne timeout"));

        webhookEventService.retryPending();

        assertThat(failed.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(2);
    }

    private WebhookEvent storedEvent(WebhookEventStatus status) {
        return WebhookEvent.builder()
                .webhookId("webhook-1")
                .eventType("WebhookTransactionPaid")
                .paymentId("BE24-T05-1")
                .status(status)
                .retryCount(status == WebhookEventStatus.FAILED ? 1 : 0)
                .build();
    }

    private WebhookEvent lastSavedEvent() {
        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository).save(captor.capture());
        return captor.getValue();
    }
}
