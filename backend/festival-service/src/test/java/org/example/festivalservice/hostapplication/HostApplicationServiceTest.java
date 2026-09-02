package org.example.festivalservice.hostapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.festivalservice.common.ApiException;
import org.example.festivalservice.domain.hostapplication.HostApplication;
import org.example.festivalservice.domain.hostapplication.HostApplicationRepository;
import org.example.festivalservice.domain.hostapplication.HostApplicationService;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.example.festivalservice.domain.hostapplication.HostApplicationResponseDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationSubmitRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class HostApplicationServiceTest {

    @Mock
    private HostApplicationRepository hostApplicationRepository;

    private HostApplicationService hostApplicationService;

    private static final HostApplicationSubmitRequestDto REQUEST =
            new HostApplicationSubmitRequestDto("소개", "010-0000-0000");

    @BeforeEach
    void setUp() {
        hostApplicationService = new HostApplicationService(hostApplicationRepository);
    }

    @Test
    void submitCreatesPendingApplication() {
        when(hostApplicationRepository.existsByUserIdAndStatus(1L, HostApplicationStatus.PENDING))
                .thenReturn(false);

        HostApplicationResponseDto response = hostApplicationService.submit(1L, "USER", REQUEST);

        assertThat(response.status()).isEqualTo(HostApplicationStatus.PENDING);
        assertThat(response.introduction()).isEqualTo("소개");
        verify(hostApplicationRepository).save(any(HostApplication.class));
    }

    @Test
    void submitRejectsAlreadyHost() {
        assertThatThrownBy(() -> hostApplicationService.submit(1L, "HOST", REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void submitRejectsDuplicatePendingApplication() {
        when(hostApplicationRepository.existsByUserIdAndStatus(1L, HostApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> hostApplicationService.submit(1L, "USER", REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getMyReturnsLatestApplication() {
        HostApplication application = new HostApplication(1L, "소개", "010-0000-0000");
        when(hostApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(application));

        HostApplicationResponseDto response = hostApplicationService.getMy(1L);

        assertThat(response.introduction()).isEqualTo("소개");
    }

    @Test
    void getMyThrowsWhenNoApplicationExists() {
        when(hostApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> hostApplicationService.getMy(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
