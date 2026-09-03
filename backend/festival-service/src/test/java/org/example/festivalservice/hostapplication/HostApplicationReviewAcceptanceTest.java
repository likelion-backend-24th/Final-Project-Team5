package org.example.festivalservice.hostapplication;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.domain.hostapplication.HostApplication;
import org.example.festivalservice.domain.hostapplication.HostApplicationRepository;
import org.example.festivalservice.domain.hostapplication.HostApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Task 4-3 — 운영자 승인 처리와 auth-service Role 부여 연동의 정상·응답유실 복구 흐름을 검증한다.
 * auth-service를 실제로 띄우지 않고 festival-service가 사용하는 RestClient 빈을 모킹해서
 * "성공"과 "응답 유실(Timeout)" 두 상황을 결정적으로 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HostApplicationReviewAcceptanceTest {

    private static final String ADMIN_ENDPOINT = "/api/admin/host-applications";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HostApplicationRepository hostApplicationRepository;

    @MockitoBean
    private RestClient authServiceRestClient;

    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    private RestClient.RequestBodySpec requestBodySpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        hostApplicationRepository.deleteAll();
        reset(authServiceRestClient);
        requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);
        when(authServiceRestClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/internal/v1/roles")).thenReturn(requestBodySpec);
    }

    // when().thenThrow()는 재스텁 시 이전 스텁(throw)이 먼저 실행돼버리므로 doReturn/doThrow 패턴을 쓴다
    private void stubAuthServiceSucceeds() {
        doReturn(responseSpec).when(requestBodySpec).retrieve();
    }

    private void stubAuthServiceTimesOut() {
        doThrow(new ResourceAccessException("connect timed out")).when(requestBodySpec).retrieve();
    }

    private HostApplication savePendingApplication() {
        return hostApplicationRepository.save(new HostApplication(1L, "소개", "010-0000-0000"));
    }

    @Test
    void approvingSucceedsImmediatelyWhenAuthServiceResponds() throws Exception {
        stubAuthServiceSucceeds();
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));
    }

    @Test
    void approvingKeepsApprovalPendingWhenAuthServiceCallFails() throws Exception {
        stubAuthServiceTimesOut();
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status", is("APPROVAL_PENDING")));
    }

    @Test
    void retryingApprovalAfterFailureRecoversToApproved() throws Exception {
        stubAuthServiceTimesOut();
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status", is("APPROVAL_PENDING")));

        stubAuthServiceSucceeds();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("APPROVED")));
    }

    @Test
    void rejectingRequiresReason() throws Exception {
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REJECT_REASON_REQUIRED")));
    }

    @Test
    void rejectingWithReasonStoresReason() throws Exception {
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","rejectReason":"자격 요건 미달"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")))
                .andExpect(jsonPath("$.data.rejectReason", is("자격 요건 미달")));
    }

    @Test
    void cannotRejectWhileApprovalIsPending() throws Exception {
        stubAuthServiceTimesOut();
        HostApplication application = savePendingApplication();
        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isAccepted());

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","rejectReason":"취소"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ALREADY_REVIEWED")));
    }

    @Test
    void reReviewingApprovedApplicationIsConflict() throws Exception {
        stubAuthServiceSucceeds();
        HostApplication application = savePendingApplication();
        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"REJECTED","rejectReason":"취소"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("ALREADY_REVIEWED")));
    }

    @Test
    void reviewingWithoutAdminRoleIsForbidden() throws Exception {
        HostApplication application = savePendingApplication();

        mockMvc.perform(patch(ADMIN_ENDPOINT + "/" + application.getId())
                        .header("X-User-Id", "9")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_ROLE")));
    }
}
