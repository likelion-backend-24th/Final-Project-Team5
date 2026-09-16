package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.example.festivalservice.domain.festival.FestivalView;
import org.example.festivalservice.domain.festival.FestivalViewRepository;
import org.example.festivalservice.domain.festival.FestivalViewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FestivalViewServiceTest {

    @Mock
    private FestivalViewRepository festivalViewRepository;

    @InjectMocks
    private FestivalViewService festivalViewService;

    @Test
    void 처음_보는_IP는_기록을_남기고_조회수를_올린다() {
        when(festivalViewRepository.findByFestivalIdAndViewerHash(eq(1L), anyString())).thenReturn(Optional.empty());

        festivalViewService.recordView(1L, "203.0.113.7");

        ArgumentCaptor<FestivalView> captor = ArgumentCaptor.forClass(FestivalView.class);
        verify(festivalViewRepository).save(captor.capture());
        assertThat(captor.getValue().getFestivalId()).isEqualTo(1L);
        assertThat(captor.getValue().getViewerHash()).hasSize(64).isNotEqualTo("203.0.113.7");
        verify(festivalViewRepository).incrementViewCount(1L);
    }

    @Test
    void 같은_IP가_24시간_안에_다시_보면_세지_않는다() {
        FestivalView recent = new FestivalView(1L, "h", LocalDateTime.now().minusHours(23));
        when(festivalViewRepository.findByFestivalIdAndViewerHash(eq(1L), anyString())).thenReturn(Optional.of(recent));

        festivalViewService.recordView(1L, "203.0.113.7");

        verify(festivalViewRepository, never()).save(any());
        verify(festivalViewRepository, never()).incrementViewCount(any());
    }

    @Test
    void 같은_IP라도_24시간이_지나면_다시_센다() {
        FestivalView old = new FestivalView(1L, "h", LocalDateTime.now().minusHours(25));
        when(festivalViewRepository.findByFestivalIdAndViewerHash(eq(1L), anyString())).thenReturn(Optional.of(old));

        festivalViewService.recordView(1L, "203.0.113.7");

        assertThat(old.getViewedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        verify(festivalViewRepository, never()).save(any());
        verify(festivalViewRepository).incrementViewCount(1L);
    }

    @Test
    void IP를_모르면_아무것도_하지_않는다() {
        festivalViewService.recordView(1L, " ");

        verifyNoInteractions(festivalViewRepository);
    }

    @Test
    void 같은_IP는_같은_해시로_다른_IP는_다른_해시로_기록한다() {
        assertThat(FestivalViewService.hash("203.0.113.7")).isEqualTo(FestivalViewService.hash("203.0.113.7"));
        assertThat(FestivalViewService.hash("203.0.113.7")).isNotEqualTo(FestivalViewService.hash("203.0.113.8"));
    }
}
