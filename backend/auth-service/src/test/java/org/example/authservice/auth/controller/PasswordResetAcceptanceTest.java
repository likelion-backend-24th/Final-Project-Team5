package org.example.authservice.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.authservice.auth.entity.EmailVerification;
import org.example.authservice.auth.repository.EmailVerificationRepository;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-09-21 감사 S1·V1 회귀 테스트 — 비밀번호 재설정은 인증한 본인이 받은 토큰이 있어야 하고,
 * 인증코드는 5번까지만 틀릴 수 있다. 틀린 횟수가 예외 롤백으로 사라지지 않는지와
 * 동시에 몰린 확인 요청도 정확히 세는지는 실제 컨트롤러~DB(H2) 경로로만 확인할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetAcceptanceTest {

    private static final String SEND_ENDPOINT = "/api/auth/email/send";
    private static final String VERIFY_ENDPOINT = "/api/auth/email/verify";
    private static final String RESET_ENDPOINT = "/api/auth/reset-password";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String PASSWORD = "password1234";
    private static final String NEW_PASSWORD = "newpassword1234";
    //발송 코드는 6자리 숫자라 이 값과는 절대 일치하지 않는다.
    private static final String WRONG_CODE = "xxxxxx";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenAnswer(call -> new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new java.util.Properties())));
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationRepository.deleteAll();
    }

    @Test
    void resetPasswordSucceedsOnlyWithTokenIssuedByVerification() throws Exception {
        signup("reset@test.com", "resetuser");
        sendCode("reset@test.com");
        String verificationToken = verifyAndGetToken("reset@test.com");

        //이메일만 알고 토큰을 모르는 요청은 인증된 기록이 있어도 거부된다.
        mockMvc.perform(post(RESET_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("reset@test.com", "guessed-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
        login("reset@test.com", NEW_PASSWORD).andExpect(status().isUnauthorized());

        mockMvc.perform(post(RESET_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("reset@test.com", verificationToken)))
                .andExpect(status().isOk());
        login("reset@test.com", NEW_PASSWORD).andExpect(status().isOk());

        //한 번 쓴 토큰으로는 다시 재설정할 수 없다.
        mockMvc.perform(post(RESET_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody("reset@test.com", verificationToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void resetPasswordWithoutTokenIsRejectedByValidation() throws Exception {
        mockMvc.perform(post(RESET_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "reset@test.com", "newPassword": "%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void fiveWrongCodesBlockVerificationEvenWithCorrectCode() throws Exception {
        sendCode("brute@test.com");

        for (int attempt = 0; attempt < 5; attempt++) {
            verify("brute@test.com", WRONG_CODE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_VERIFICATION_CODE"));
        }

        //예외 응답이었어도 틀린 횟수는 롤백되지 않고 남아 있어야 한다.
        verify("brute@test.com", latestCode("brute@test.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_VERIFY_ATTEMPTS"));
        assertThat(latestVerification("brute@test.com").isVerified()).isFalse();
    }

    @Test
    void concurrentWrongCodesAreCountedExactly() throws Exception {
        sendCode("burst@test.com");
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);

        List<MockHttpServletResponse> responses = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(requests)) {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return verify("burst@test.com", WRONG_CODE).andReturn().getResponse();
                }));
            }
            //모든 요청을 한꺼번에 출발시켜 틀린 횟수를 동시에 읽는 상황을 만든다.
            start.countDown();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
        }

        assertThat(responses).filteredOn(response -> response.getStatus() == 400).hasSize(5);
        assertThat(responses).filteredOn(response -> response.getStatus() == 429).hasSize(5);
        assertThat(latestVerification("burst@test.com").getFailedAttempts()).isEqualTo(5);
    }

    private void signup(String username, String nickname) throws Exception {
        sendCode(username);
        verify(username, latestCode(username)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "홍길동",
                                  "username": "%s",
                                  "nickname": "%s",
                                  "password": "%s",
                                  "termsAgreed": true
                                }""".formatted(username, nickname, PASSWORD)))
                .andExpect(status().isCreated());
        //가입 때 쓴 발송 기록이 다음 발송의 30초 쿨다운에 걸리지 않도록 정리한다.
        emailVerificationRepository.deleteAll();
    }

    private void sendCode(String email) throws Exception {
        mockMvc.perform(post(SEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}""".formatted(email)))
                .andExpect(status().isOk());
    }

    private String verifyAndGetToken(String email) throws Exception {
        String body = verify(email, latestCode(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.verificationToken");
    }

    private org.springframework.test.web.servlet.ResultActions verify(String email, String code) throws Exception {
        return mockMvc.perform(post(VERIFY_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "code": "%s"}""".formatted(email, code)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}""".formatted(username, password)));
    }

    private String resetBody(String username, String verificationToken) {
        return """
                {
                  "username": "%s",
                  "verificationToken": "%s",
                  "newPassword": "%s"
                }""".formatted(username, verificationToken, NEW_PASSWORD);
    }

    private String latestCode(String email) {
        return latestVerification(email).getCode();
    }

    private EmailVerification latestVerification(String email) {
        return emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email).orElseThrow();
    }
}
