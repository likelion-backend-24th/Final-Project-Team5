package org.example.authservice.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Task 2-4 — Story 2(회원가입/로그인) Acceptance Test.
 * 정상 가입→로그인→재발급 흐름, 중복·검증 실패, 로그인 실패, Refresh Token 만료·재사용 탐지,
 * 인증 없는 보호 API 접근을 실제 컨트롤러~DB(H2) 경로로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAuthAcceptanceTest {

    private static final String SIGNUP_ENDPOINT = "/api/auth/signup";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String REISSUE_ENDPOINT = "/api/auth/reissue";
    private static final String ME_ENDPOINT = "/api/users/me";
    private static final String PASSWORD = "password1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupThenLoginThenReissueSucceeds() throws Exception {
        signup("flow@test.com", "flowuser");

        MvcResult loginResult = login("flow@test.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(cookie().exists("refreshToken"))
                .andReturn();
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

        // refresh token은 subject(username)+발급시각(초 단위)만으로 서명되어, 같은 유저에게
        // 같은 초 안에 두 번 발급하면 완전히 같은 토큰 문자열이 나와 token_hash unique 제약에
        // 걸린다. 테스트가 실제 네트워크 지연 없이 즉시 이어서 호출하므로 최소 간격을 둔다.
        Thread.sleep(1000);

        mockMvc.perform(post(REISSUE_ENDPOINT).cookie(refreshTokenCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void signupWithDuplicateUsernameIsConflict() throws Exception {
        signup("dup@test.com", "dupuser1");

        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("dup@test.com", "dupuser2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_USERNAME")));
    }

    @Test
    void signupWithBlankNameFailsValidation() throws Exception {
        String body = """
                {
                  "name": "",
                  "username": "blank@test.com",
                  "nickname": "blankuser",
                  "password": "%s"
                }""".formatted(PASSWORD);

        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        signup("wrongpw@test.com", "wrongpwuser");

        login("wrongpw@test.com", "incorrect-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("INVALID_PASSWORD")));
    }

    @Test
    void reissueWithExpiredRefreshTokenIsUnauthorized() throws Exception {
        signup("expired@test.com", "expireduser");
        MvcResult loginResult = login("expired@test.com", PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");

        // DB에 저장된 만료 시각을 과거로 돌려 "만료된 refresh token" 상황을 재현한다.
        User user = userRepository.findByUsername("expired@test.com").orElseThrow();
        RefreshToken savedToken = refreshTokenRepository.findByUser(user).orElseThrow();
        savedToken.setExpiresAt(LocalDateTime.now().minusDays(1));
        refreshTokenRepository.save(savedToken);

        mockMvc.perform(post(REISSUE_ENDPOINT).cookie(refreshTokenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("INVALID_REFRESH_TOKEN")));
    }

    @Test
    void reusingRotatedRefreshTokenRevokesAllSessions() throws Exception {
        signup("reuse@test.com", "reuseuser");
        MvcResult loginResult = login("reuse@test.com", PASSWORD)
                .andExpect(status().isOk())
                .andReturn();
        Cookie originalRefreshTokenCookie = loginResult.getResponse().getCookie("refreshToken");
        Thread.sleep(1000);

        // 정상적인 1차 재발급 — 이 시점에 원래 토큰은 Rotation으로 폐기(revoke)된다.
        mockMvc.perform(post(REISSUE_ENDPOINT).cookie(originalRefreshTokenCookie))
                .andExpect(status().isOk());

        // 이미 폐기된 예전 토큰을 다시 사용 — 탈취로 간주해 401 + 전체 세션 강제 로그아웃.
        mockMvc.perform(post(REISSUE_ENDPOINT).cookie(originalRefreshTokenCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("REFRESH_TOKEN_REUSED")));

        User user = userRepository.findByUsername("reuse@test.com").orElseThrow();
        assertThat(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId())).isEmpty();
    }

    @Test
    void accessingProtectedApiWithoutUserIdHeaderFails() throws Exception {
        // 게이트웨이를 거치지 않고 auth-service에 직접 요청하는 상황을 재현한다.
        // 게이트웨이가 X-User-Id 헤더를 채워주지 않으면 auth-service는 이 요청을 처리할 수 없다.
        mockMvc.perform(get(ME_ENDPOINT))
                .andExpect(status().isBadRequest());
    }

    private void signup(String username, String nickname) throws Exception {
        mockMvc.perform(post(SIGNUP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(username, nickname)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(username, password)));
    }

    private String signupBody(String username, String nickname) {
        return """
                {
                  "name": "홍길동",
                  "username": "%s",
                  "nickname": "%s",
                  "password": "%s"
                }""".formatted(username, nickname, PASSWORD);
    }

    private String loginBody(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }""".formatted(username, password);
    }
}
