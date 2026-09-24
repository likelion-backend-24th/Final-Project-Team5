package org.example.paymentservice.domain.webhook;

import org.example.paymentservice.domain.cancellation.PaymentCancellationService;
import org.example.paymentservice.domain.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 배치 → 재시도 대상 조회(JPQL) → WebhookEventService 재처리까지 실제 DB(H2)로 확인한다.
 * 결제 동기화 자체는 대역으로 바꿔 어떤 결제가 다시 처리됐는지만 본다.
 * 스케줄 실행이 테스트 중간에 끼어들지 않도록 배치 빈은 켜지 않고 직접 만들어 호출한다.
 */
@SpringBootTest
class WebhookRetrySchedulerTest {

    @Autowired
    private WebhookEventService webhookEventService;

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentCancellationService paymentCancellationService;

    private WebhookRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        webhookEventRepository.deleteAll();
        scheduler = new WebhookRetryScheduler(webhookEventService);
    }

    private WebhookEvent saved(String webhookId, WebhookEventStatus status, int retryCount) {
        return webhookEventRepository.save(WebhookEvent.builder()
                .webhookId(webhookId)
                .eventType("WebhookTransactionPaid")
                .paymentId("BE24-T05-" + webhookId)
                .status(status)
                .retryCount(retryCount)
                .build());
    }

    private void receivedAt(WebhookEvent event, LocalDateTime createdAt) {
        //created_at은 수정 불가 컬럼이라 저장 시각을 과거로 돌릴 때만 SQL로 직접 바꾼다.
        jdbcTemplate.update("update webhook_events set created_at = ? where id = ?", createdAt, event.getId());
    }

    @Test
    void 실패했거나_처리_도중_멈춘_웹훅만_다시_처리한다() {
        saved("failed", WebhookEventStatus.FAILED, 1);
        saved("exhausted", WebhookEventStatus.FAILED, WebhookEventService.MAX_RETRY_COUNT);
        receivedAt(saved("stuck", WebhookEventStatus.RECEIVED, 0), LocalDateTime.now().minusMinutes(10));
        saved("in-flight", WebhookEventStatus.RECEIVED, 0);
        saved("processed", WebhookEventStatus.PROCESSED, 0);
        saved("ignored", WebhookEventStatus.IGNORED, 0);
        saved("legacy", null, 0);

        scheduler.run();

        verify(paymentService).syncPayment("BE24-T05-failed");
        verify(paymentService).syncPayment("BE24-T05-stuck");
        verify(paymentService, org.mockito.Mockito.times(2)).syncPayment(anyString());
        assertThat(webhookEventRepository.findByWebhookId("failed").orElseThrow().getStatus())
                .isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(webhookEventRepository.findByWebhookId("stuck").orElseThrow().getStatus())
                .isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(webhookEventRepository.findByWebhookId("exhausted").orElseThrow().getStatus())
                .isEqualTo(WebhookEventStatus.FAILED);
    }

    @Test
    void 재시도가_또_실패하면_실패_횟수만_늘리고_배치는_계속_돈다() {
        saved("failed", WebhookEventStatus.FAILED, 1);
        doThrow(new org.springframework.web.client.ResourceAccessException("PortOne timeout"))
                .when(paymentService).syncPayment("BE24-T05-failed");

        scheduler.run();

        WebhookEvent event = webhookEventRepository.findByWebhookId("failed").orElseThrow();
        assertThat(event.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getLastError()).isEqualTo("ResourceAccessException");
    }

    @Test
    void 재시도_대상이_없으면_아무것도_하지_않는다() {
        saved("processed", WebhookEventStatus.PROCESSED, 0);

        scheduler.run();

        verify(paymentService, never()).syncPayment(anyString());
    }
}
