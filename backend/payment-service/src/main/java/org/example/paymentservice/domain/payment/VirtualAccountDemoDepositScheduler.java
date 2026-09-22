package org.example.paymentservice.domain.payment;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 가상계좌(무통장입금) 데모 자동 입금 배치. 이 사이트는 실제 입금이 일어나지 않는 테스트 채널만 쓰므로,
 * 계좌 발급 후 일정 시간(기본 30분)이 지나면 입금이 확인된 것으로 보고 결제를 완료 처리한다.
 * 실결제로 전환하면 payment.demo-deposit.enabled=false로 반드시 꺼야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payment.demo-deposit.enabled", matchIfMissing = true)
public class VirtualAccountDemoDepositScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${payment.demo-deposit.delay-minutes:30}")
    private long delayMinutes;

    @Scheduled(fixedDelayString = "${payment.demo-deposit.poll-ms:60000}")
    public void run() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(delayMinutes));
        for (Payment payment : paymentRepository.findByStatusAndVirtualAccountIssuedAtBefore(
                PaymentStatus.VIRTUAL_ACCOUNT_ISSUED, cutoff)) {
            try {
                paymentService.demoDeposit(payment.getPaymentId());
                log.info("데모 자동 입금 처리: paymentId={}", payment.getPaymentId());
            } catch (RuntimeException e) {
                // 예매 확정 실패 등은 다음 주기에 같은 결제를 다시 집어 재시도한다.
                log.warn("데모 자동 입금 재시도 필요: paymentId={}", payment.getPaymentId(), e);
            }
        }
    }
}
