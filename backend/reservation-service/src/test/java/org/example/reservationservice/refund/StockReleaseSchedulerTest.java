package org.example.reservationservice.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.example.reservationservice.domain.refund.StockReleaseQueue;
import org.example.reservationservice.domain.refund.StockReleaseQueueRepository;
import org.example.reservationservice.domain.refund.StockReleaseScheduler;
import org.example.reservationservice.infrastructure.festival.FestivalServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

class StockReleaseSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private StockReleaseQueueRepository repository;
    private FestivalServiceClient festivalServiceClient;
    private StockReleaseScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(StockReleaseQueueRepository.class);
        festivalServiceClient = mock(FestivalServiceClient.class);
        scheduler = new StockReleaseScheduler(repository, festivalServiceClient);
        ReflectionTestUtils.setField(scheduler, "appTimezone", "Asia/Seoul");
        ReflectionTestUtils.setField(scheduler, "releaseHour", 19);
    }

    @Test
    @DisplayName("오후 7시 전에 환불된 재고는 그날 오후 7시에 풀린다")
    void refundBeforeSevenReleasesSameDay() {
        Instant now = LocalDateTime.of(2026, 9, 14, 10, 30).atZone(SEOUL).toInstant();

        Instant release = scheduler.nextReleaseInstant(now);

        assertThat(LocalDateTime.ofInstant(release, SEOUL)).isEqualTo(LocalDateTime.of(2026, 9, 14, 19, 0));
    }

    @Test
    @DisplayName("오후 7시 이후에 환불된 재고는 다음 날 오후 7시에 풀린다")
    void refundAtOrAfterSevenReleasesNextDay() {
        Instant exactlySeven = LocalDateTime.of(2026, 9, 14, 19, 0).atZone(SEOUL).toInstant();
        Instant lateEvening = LocalDateTime.of(2026, 9, 14, 23, 59).atZone(SEOUL).toInstant();

        assertThat(LocalDateTime.ofInstant(scheduler.nextReleaseInstant(exactlySeven), SEOUL))
                .isEqualTo(LocalDateTime.of(2026, 9, 15, 19, 0));
        assertThat(LocalDateTime.ofInstant(scheduler.nextReleaseInstant(lateEvening), SEOUL))
                .isEqualTo(LocalDateTime.of(2026, 9, 15, 19, 0));
    }

    @Test
    @DisplayName("풀 시각이 지난 항목은 재고를 되돌리고 released로 표시한다")
    void releasesDueEntries() {
        StockReleaseQueue entry = new StockReleaseQueue(1L, 21L, 2, Instant.now().minusSeconds(60));
        when(repository.findByReleasedAtIsNullAndReleaseAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry));

        scheduler.releaseDueStock();

        verify(festivalServiceClient).restoreStock(21L, 2);
        assertThat(entry.getReleasedAt()).isNotNull();
    }

    @Test
    @DisplayName("재고 복구 호출이 실패하면 released로 표시하지 않아 다음 회차에 재시도된다")
    void keepsEntryWhenRestoreFails() {
        StockReleaseQueue entry = new StockReleaseQueue(1L, 21L, 2, Instant.now().minusSeconds(60));
        when(repository.findByReleasedAtIsNullAndReleaseAtBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(entry));
        doThrow(new ResourceAccessException("timeout")).when(festivalServiceClient).restoreStock(anyLong(), anyInt());

        scheduler.releaseDueStock();

        assertThat(entry.getReleasedAt()).isNull();
    }
}
