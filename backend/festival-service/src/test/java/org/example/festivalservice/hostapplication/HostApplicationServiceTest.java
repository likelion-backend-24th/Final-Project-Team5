package org.example.festivalservice.hostapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.festivalservice.common.ApiException;
import org.example.festivalservice.domain.hostapplication.HostApplication;
import org.example.festivalservice.domain.hostapplication.HostApplicationRepository;
import org.example.festivalservice.domain.hostapplication.HostApplicationService;
import org.example.festivalservice.domain.hostapplication.HostApplicationSetHostRequestDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.example.festivalservice.domain.hostapplication.HostApplicationResponseDto;
import org.example.festivalservice.domain.hostapplication.HostApplicationSubmitRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HostApplicationServiceTest {

    @Mock
    private HostApplicationRepository hostApplicationRepository;

    @Mock
    private RestClient authServiceRestClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private HostApplicationService hostApplicationService;

    private static final HostApplicationSubmitRequestDto REQUEST =
            new HostApplicationSubmitRequestDto("소개", "010-0000-0000");

    @BeforeEach
    void setUp() {
        hostApplicationService = new HostApplicationService(hostApplicationRepository, authServiceRestClient);
        ReflectionTestUtils.setField(hostApplicationService, "internalAuthToken", "test-token");
    }

    //승인 케이스에서만 auth-service 호출 체인을 stub — verifyNoInteractions/never()가 stub 설정 자체를 호출로 오인하지 않도록 공용 셋업에 두지 않음
    private void stubGrantHostRoleCall() {
        when(authServiceRestClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/internal/v1/roles")).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
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

    @Test
    void reviewApprovingCallsAuthServiceAndSetsApproved() {
        stubGrantHostRoleCall();
        HostApplication application = new HostApplication(1L, "소개", "010-0000-0000");
        ReflectionTestUtils.setField(application, "id", 10L);
        when(hostApplicationRepository.findById(10L)).thenReturn(Optional.of(application));
        HostApplicationSetHostRequestDto request = new HostApplicationSetHostRequestDto();
        ReflectionTestUtils.setField(request, "status", HostApplicationStatus.APPROVED);

        HostApplicationResponseDto response = hostApplicationService.review(10L, "ADMIN", request);

        assertThat(response.status()).isEqualTo(HostApplicationStatus.APPROVED);
        verify(authServiceRestClient).put();
    }

    @Test
    void reviewRejectingDoesNotCallAuthService() {
        HostApplication application = new HostApplication(1L, "소개", "010-0000-0000");
        ReflectionTestUtils.setField(application, "id", 10L);
        when(hostApplicationRepository.findById(10L)).thenReturn(Optional.of(application));
        HostApplicationSetHostRequestDto request = new HostApplicationSetHostRequestDto();
        ReflectionTestUtils.setField(request, "status", HostApplicationStatus.REJECTED);

        HostApplicationResponseDto response = hostApplicationService.review(10L, "ADMIN", request);

        assertThat(response.status()).isEqualTo(HostApplicationStatus.REJECTED);
        verifyNoInteractions(authServiceRestClient);
    }

    @Test
    void reviewWithoutAdminRoleIsForbidden() {
        HostApplicationSetHostRequestDto request = new HostApplicationSetHostRequestDto();
        ReflectionTestUtils.setField(request, "status", HostApplicationStatus.APPROVED);

        assertThatThrownBy(() -> hostApplicationService.review(10L, "HOST", request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(authServiceRestClient, never()).put();
    }

    @Test
    void reviewAlreadyReviewedApplicationIsConflict() {
        HostApplication application = new HostApplication(1L, "소개", "010-0000-0000");
        ReflectionTestUtils.setField(application, "id", 10L);
        ReflectionTestUtils.setField(application, "status", HostApplicationStatus.APPROVED);
        when(hostApplicationRepository.findById(10L)).thenReturn(Optional.of(application));
        HostApplicationSetHostRequestDto request = new HostApplicationSetHostRequestDto();
        ReflectionTestUtils.setField(request, "status", HostApplicationStatus.APPROVED);

        assertThatThrownBy(() -> hostApplicationService.review(10L, "ADMIN", request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
