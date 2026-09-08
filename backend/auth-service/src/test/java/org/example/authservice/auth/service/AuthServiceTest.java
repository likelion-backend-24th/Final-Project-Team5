package org.example.authservice.auth.service;

import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.exception.EmailVerificationErrorCode;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.security.JwtTokenProvider;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    private SignupRequest createValidRequest() {
        return new SignupRequest(
                "홍길동",
                "test@naver.com",
                "안양개발자",
                "test1234",
                true // termsAgreed
        );
    }

    @Test
    @DisplayName("정상적인 요청이면 회원가입에 성공하고, 비밀번호는 암호화되어 저장되며 약관동의 시각이 기록된다")
    void signup_success() {
        // given
        SignupRequest request = createValidRequest();
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(userRepository.existsByNickname(request.getNickname())).willReturn(false);
        given(passwordEncoder.encode(request.getPassword())).willReturn("encoded-password");

        // when
        authService.signup(request);

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());

        User savedUser = captor.getValue();
        assertThat(savedUser.getName()).isEqualTo("홍길동");
        assertThat(savedUser.getUsername()).isEqualTo("test@naver.com");
        assertThat(savedUser.getNickname()).isEqualTo("안양개발자");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedUser.getTermsAgreeAt()).isNotNull();
    }

    @Test
    @DisplayName("username이 이미 존재하면 DUPLICATE_USERNAME 예외가 발생하고, save는 호출되지 않는다")
    void signup_fail_duplicateUsername() {
        // given
        SignupRequest request = createValidRequest();
        given(userRepository.existsByUsername(request.getUsername())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.DUPLICATE_USERNAME));

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByNickname(any());
    }

    @Test
    @DisplayName("nickname이 이미 존재하면 DUPLICATE_NICKNAME 예외가 발생하고, save는 호출되지 않는다")
    void signup_fail_duplicateNickname() {
        // given
        SignupRequest request = createValidRequest();
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(userRepository.existsByNickname(request.getNickname())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.DUPLICATE_NICKNAME));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 TERMS_NOT_AGREED 예외가 발생하고, save는 호출되지 않는다")
    void signup_fail_termsNotAgreed() {
        // given
        SignupRequest request = new SignupRequest(
                "홍길동",
                "test@naver.com",
                "안양개발자",
                "test1234",
                false // termsAgreed = false
        );
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(userRepository.existsByNickname(request.getNickname())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.TERMS_NOT_AGREED));

        verify(userRepository, never()).save(any());
        verify(emailVerificationService, never()).checkVerified(any());
    }

    @Test
    @DisplayName("비밀번호는 반드시 PasswordEncoder를 거쳐서 저장된다 (평문 저장 방지)")
    void signup_passwordIsEncoded() {
        // given
        SignupRequest request = createValidRequest();
        given(userRepository.existsByUsername(request.getUsername())).willReturn(false);
        given(userRepository.existsByNickname(request.getNickname())).willReturn(false);
        given(passwordEncoder.encode("test1234")).willReturn("encoded-password");

        // when
        authService.signup(request);

        // then
        verify(passwordEncoder, times(1)).encode("test1234");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("test1234");
    }

    @Test
    @DisplayName("정상적인 로그인 요청이면 Access/Refresh Token을 발급하고, 실패 카운트/잠금을 초기화한다")
    void login_success() {
        // given
        User user = createActiveUser();
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository, times(1)).save(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo("refresh-token");
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("존재하지 않는 username이면 USER_NOT_FOUND 예외가 발생한다")
    void login_fail_userNotFound() {
        // given
        LoginRequest request = new LoginRequest("notexist@naver.com", "test1234");
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.USER_NOT_FOUND));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_PASSWORD 예외가 발생하고, 실패 기록이 위임된다")
    void login_fail_invalidPassword() {
        // given
        User user = createActiveUser();
        LoginRequest request = new LoginRequest("test@naver.com", "wrongpassword");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_PASSWORD));

        verify(loginAttemptService, times(1)).recordFailedLoginAttempt(user);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("계정이 잠긴 상태면 비밀번호가 맞아도 ACCOUNT_LOCKED 예외가 발생하고, 비밀번호 검증 자체를 하지 않는다")
    void login_fail_accountLocked() {
        // given
        User user = createActiveUser();
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_LOCKED));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginAttemptService, never()).recordFailedLoginAttempt(any());
    }

    @Test
    @DisplayName("잠금 시간이 지났으면 정상적으로 로그인 시도가 진행된다")
    void login_success_afterLockExpired() {
        // given
        User user = createActiveUser();
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("정지된 계정이면 ACCOUNT_SUSPENDED 예외가 발생한다")
    void login_fail_accountSuspended() {
        // given
        User user = createActiveUser();
        user.setStatus(AccountStatus.SUSPENDED);
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_SUSPENDED));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 ACCOUNT_WITHDRAWN 예외가 발생한다")
    void login_fail_accountWithdrawn() {
        // given
        User user = createActiveUser();
        user.setStatus(AccountStatus.WITHDRAWN);
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_WITHDRAWN));
    }

    // 헬퍼: 활성 상태 유저 생성
    private User createActiveUser() {
        User user = new User();
        user.setName("홍길동");
        user.setUsername("test@naver.com");
        user.setPassword("encoded-password");
        user.setNickname("안양개발자");
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }

    @Test
    @DisplayName("정상적인 토큰이면 재발급에 성공하고, 기존 토큰은 폐기되며 새 토큰과 연결된다")
    void reissue_success() {
        // given
        User user = createActiveUser();
        RefreshToken savedToken = createSavedRefreshToken(user);
        String rawRefreshToken = "old-refresh-token";

        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(savedToken));
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                .willReturn("new-refresh-token");

        // when
        TokenResponse response = authService.reissue(rawRefreshToken);

        // then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(savedToken.getRevokedAt()).isNotNull();

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("서명이 유효하지 않거나 만료된 JWT면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissue_fail_invalidJwt() {
        // given
        String rawRefreshToken = "invalid-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("DB에 없는 토큰이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissue_fail_tokenNotFound() {
        // given
        String rawRefreshToken = "not-in-db-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("이미 폐기된 토큰이 재사용되면 REFRESH_TOKEN_REUSED 예외가 발생하고, 해당 유저의 모든 활성 토큰 폐기를 위임한다")
    void reissue_fail_tokenReused() {
        // given
        User user = createActiveUser();
        RefreshToken revokedToken = createSavedRefreshToken(user);
        revokedToken.setRevokedAt(LocalDateTime.now().minusMinutes(5));

        String rawRefreshToken = "reused-token";

        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(revokedToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));

        verify(refreshTokenRevocationService, times(1)).revokeAllTokens(user);
    }

    @Test
    @DisplayName("DB 상 만료된 토큰이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissue_fail_expiredInDb() {
        // given
        User user = createActiveUser();
        RefreshToken expiredToken = createSavedRefreshToken(user);
        expiredToken.setExpiresAt(LocalDateTime.now().minusDays(1));

        String rawRefreshToken = "expired-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(expiredToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("정지된 계정의 토큰이면 ACCOUNT_SUSPENDED 예외가 발생한다")
    void reissue_fail_accountSuspended() {
        // given
        User user = createActiveUser();
        user.setStatus(AccountStatus.SUSPENDED);
        RefreshToken savedToken = createSavedRefreshToken(user);

        String rawRefreshToken = "valid-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(savedToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_SUSPENDED));
    }

    // 헬퍼: DB에 저장된 상태를 흉내낸 RefreshToken (활성 상태)
    private RefreshToken createSavedRefreshToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("some-hash");
        token.setExpiresAt(LocalDateTime.now().plusDays(14));
        return token;
    }

    // 헬퍼: 테스트에서 쓸 해시 계산 (AuthService의 hashToken과 동일 로직)
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("정상적인 토큰으로 로그아웃하면 해당 토큰이 revoked 처리된다")
    void logout_success() {
        // given
        RefreshToken savedToken = createSavedRefreshToken(createActiveUser());
        String rawRefreshToken = "valid-refresh-token";

        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(savedToken));

        // when
        authService.logout(rawRefreshToken);

        // then
        assertThat(savedToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(1)).save(savedToken);
    }

    @Test
    @DisplayName("쿠키가 없으면(null) 아무 처리도 하지 않고 조용히 종료한다")
    void logout_withNullToken_doesNothing() {
        // when
        authService.logout(null);

        // then
        verify(refreshTokenRepository, never()).findByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("DB에 없는 토큰으로 로그아웃해도 예외 없이 조용히 종료한다")
    void logout_withUnknownToken_doesNothing() {
        // given
        String rawRefreshToken = "not-in-db-token";
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.empty());

        // when & then
        authService.logout(rawRefreshToken);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일 인증 완료 후 비밀번호 재설정에 성공하고, 기존 세션이 전부 무효화된다")
    void resetPassword_success() {
        // given
        User user = createActiveUser();
        given(userRepository.findByUsername("test@naver.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newpassword1234")).willReturn("encoded-new-password");

        // when
        authService.resetPassword("test@naver.com", "newpassword1234");

        // then
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(userRepository, times(1)).save(user);
        verify(emailVerificationService, times(1)).checkVerified("test@naver.com");
        verify(refreshTokenRevocationService, times(1)).revokeAllTokens(user);
    }

    @Test
    @DisplayName("이메일 인증이 완료되지 않았으면 EMAIL_NOT_VERIFIED 예외가 발생하고, 비밀번호는 변경되지 않는다")
    void resetPassword_fail_emailNotVerified() {
        // given
        doThrow(new ApiException(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).checkVerified("notverified@naver.com");

        // when & then
        assertThatThrownBy(() -> authService.resetPassword("notverified@naver.com", "newpassword1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED));

        verify(userRepository, never()).findByUsername(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRevocationService, never()).revokeAllTokens(any());
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 USER_NOT_FOUND 예외가 발생한다")
    void resetPassword_fail_userNotFound() {
        // given
        given(userRepository.findByUsername("notexist@naver.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.resetPassword("notexist@naver.com", "newpassword1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.USER_NOT_FOUND));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRevocationService, never()).revokeAllTokens(any());
    }
}