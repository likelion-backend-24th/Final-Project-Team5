package org.example.festivalservice.domain.hostapplication;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 처리 중 auth-service 응답을 못 받아 APPROVAL_PENDING에 멈춘 주최자 신청을 주기적으로 재시도한다.
 * 실제로 라이브에서 Role은 부여됐는데 응답이 read timeout(2초)을 넘겨 신청만 대기 상태로 남은 사례가 있었다.
 * 운영자가 다시 승인 버튼을 누르지 않아도 스스로 회복되도록 한다.
 */
@Component
@RequiredArgsConstructor
public class HostApplicationApprovalRetryScheduler {

    private static final int RETRY_AFTER_SECONDS = 30;

    private final HostApplicationService hostApplicationService;

    @Scheduled(fixedDelay = 60_000)
    public void retryStuckApprovals() {
        hostApplicationService.retryPendingApprovals(LocalDateTime.now().minusSeconds(RETRY_AFTER_SECONDS));
    }
}
