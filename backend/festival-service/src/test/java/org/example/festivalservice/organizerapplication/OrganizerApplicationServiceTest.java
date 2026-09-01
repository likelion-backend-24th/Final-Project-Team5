package org.example.festivalservice.organizerapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.festivalservice.common.ApiException;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationResponse;
import org.example.festivalservice.organizerapplication.dto.OrganizerApplicationSubmitRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrganizerApplicationServiceTest {

    @Mock
    private OrganizerApplicationRepository organizerApplicationRepository;

    private OrganizerApplicationService organizerApplicationService;

    private static final OrganizerApplicationSubmitRequest REQUEST =
            new OrganizerApplicationSubmitRequest("소개", "010-0000-0000");

    @BeforeEach
    void setUp() {
        organizerApplicationService = new OrganizerApplicationService(organizerApplicationRepository);
    }

    @Test
    void submitCreatesPendingApplication() {
        when(organizerApplicationRepository.existsByUserIdAndStatus(1L, OrganizerApplicationStatus.PENDING))
                .thenReturn(false);

        OrganizerApplicationResponse response = organizerApplicationService.submit(1L, "USER", REQUEST);

        assertThat(response.status()).isEqualTo(OrganizerApplicationStatus.PENDING);
        assertThat(response.introduction()).isEqualTo("소개");
        verify(organizerApplicationRepository).save(any(OrganizerApplication.class));
    }

    @Test
    void submitRejectsAlreadyOrganizer() {
        assertThatThrownBy(() -> organizerApplicationService.submit(1L, "HOST", REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void submitRejectsDuplicatePendingApplication() {
        when(organizerApplicationRepository.existsByUserIdAndStatus(1L, OrganizerApplicationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> organizerApplicationService.submit(1L, "USER", REQUEST))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getMyReturnsLatestApplication() {
        OrganizerApplication application = new OrganizerApplication(1L, "소개", "010-0000-0000");
        when(organizerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(application));

        OrganizerApplicationResponse response = organizerApplicationService.getMy(1L);

        assertThat(response.introduction()).isEqualTo("소개");
    }

    @Test
    void getMyThrowsWhenNoApplicationExists() {
        when(organizerApplicationRepository.findFirstByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizerApplicationService.getMy(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
