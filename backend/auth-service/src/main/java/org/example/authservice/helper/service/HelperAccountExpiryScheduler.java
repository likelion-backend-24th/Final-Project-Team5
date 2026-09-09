package org.example.authservice.helper.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 페스티벌이 끝나고 유예시간이 지난 도우미 계정을 자동으로 삭제한다.
 * 계정 발급 시 저장해둔 festivalEndAt 스냅샷만 보고 판단하므로 festival-service를 호출하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class HelperAccountExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(HelperAccountExpiryScheduler.class);

    private final HelperAccountService helperAccountService;

    //페스티벌 종료 후 계정을 얼마나 더 살려둘지. 정산·누락 확인 여유를 두려고 기본 24시간으로 잡았다.
    @Value("${helper.account.revoke-after-hours:24}")
    private long revokeAfterHours;

    //비교 기준 타임존. festivalEndAt이 호스트가 입력한 타임존 없는 벽시계(KST)라서 서버 기본
    //타임존으로 now()를 뽑으면 UTC 환경에서 9시간 어긋난다 — 서버 설정과 무관하게 같은 결과를 내도록 명시한다.
    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    //회수는 하루 단위 작업이라 분 단위 정밀도가 필요 없다 — 10분마다 확인한다.
    @Scheduled(fixedDelay = 600_000)
    public void deleteExpiredHelperAccounts() {
        LocalDateTime revokeThreshold =
                LocalDateTime.now(ZoneId.of(appTimezone)).minusHours(revokeAfterHours);
        int deletedCount = helperAccountService.deleteExpiredHelperAccounts(revokeThreshold);

        //삭제는 되돌릴 수 없으니 몇 개를 지웠는지는 항상 남긴다.
        if (deletedCount > 0) {
            log.info("페스티벌 종료 후 {}시간이 지난 도우미 계정 {}개를 삭제했습니다.",
                    revokeAfterHours, deletedCount);
        }
    }
}
