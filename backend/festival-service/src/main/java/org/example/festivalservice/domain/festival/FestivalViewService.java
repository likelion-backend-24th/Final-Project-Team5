package org.example.festivalservice.domain.festival;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페스티벌 조회수 — 같은 IP는 24시간에 한 번만 센다. 인기 정렬(sort=viewCount,desc)의 근거 값이다.
 * 집계 실패가 상세 조회 자체를 막으면 안 되므로 호출자는 예외를 받지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FestivalViewService {

    private static final Logger log = LoggerFactory.getLogger(FestivalViewService.class);

    static final int COUNT_WINDOW_HOURS = 24;

    private final FestivalViewRepository festivalViewRepository;

    //상세 조회와 별도 트랜잭션. 같은 IP가 거의 동시에 두 번 열면 한쪽이 유니크 제약에 걸려 커밋 시점에
    //예외가 나는데(이미 센 것이니 무시해도 된다), 그 예외는 호출자(컨트롤러)가 삼킨다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordView(Long festivalId, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String viewerHash = hash(clientIp.trim());
        Optional<FestivalView> existing = festivalViewRepository.findByFestivalIdAndViewerHash(festivalId, viewerHash);
        if (existing.isEmpty()) {
            festivalViewRepository.save(new FestivalView(festivalId, viewerHash, now));
        } else if (existing.get().getViewedAt().isBefore(now.minusHours(COUNT_WINDOW_HOURS))) {
            existing.get().touch(now);
        } else {
            return;
        }
        festivalViewRepository.incrementViewCount(festivalId);
    }

    //24시간이 지난 행은 어차피 다시 셀 수 있는 상태라 지워도 결과가 같다. 새벽에 한 번 정리한다.
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredViews() {
        int deleted = festivalViewRepository.deleteByViewedAtBefore(LocalDateTime.now().minusHours(COUNT_WINDOW_HOURS));
        if (deleted > 0) {
            log.info("24시간 지난 조회 기록 {}건 정리", deleted);
        }
    }

    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM", e);
        }
    }
}
