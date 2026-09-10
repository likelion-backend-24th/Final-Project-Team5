package org.example.authservice.auth.service;

import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.dto.oauth.GoogleUserInfoResponse;
import org.example.authservice.auth.dto.oauth.KakaoUserInfoResponse;
import org.example.authservice.auth.entity.OauthAccount;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.exception.EmailVerificationErrorCode;
import org.example.authservice.auth.repository.OauthAccountRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.HttpClientErrorException;

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

    @Mock
    private KakaoApiClient kakaoApiClient;

    @Mock
    private GoogleApiClient googleApiClient;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @InjectMocks
    private AuthService authService;

    private SignupRequest createValidRequest() {
        return new SignupRequest(
                "홍길동",
                "test@naver.com",
                "안양개발자",
                "test1234",
                true
        );
    }

    // ===== signup =====

    @Test
    @DisplayName("정상적인 요청이면 회원가입에 성공하고, 비밀번호는 암호화되어 저장되며 약관동의 시각이 기록된다")
    void signup_success() {
        // given
        SignupRequest request = createValidRequest();
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());
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
    @DisplayName("username이 이미 존재하고 비밀번호도 있으면 DUPLICATE_USERNAME 예외가 발생하고, save는 호출되지 않는다")
    void signup_fail_duplicateUsername() {
        // given
        SignupRequest request = createValidRequest();
        User existingUser = createActiveUser();
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(existingUser));

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
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());
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
        SignupRequest request = new SignupRequest("홍길동", "test@naver.com", "안양개발자", "test1234", false);
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());
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
        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.empty());
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
    @DisplayName("이미 소셜 계정이 있는 이메일로 일반 회원가입 시도하면 비밀번호만 추가되고 새 유저는 생성되지 않는다")
    void signup_success_mergePasswordIntoExistingSocialUser() {
        // given
        User socialUser = createActiveUser();
        socialUser.setPassword(null);
        SignupRequest request = new SignupRequest(
                "홍길동",
                "test@naver.com",
                "다른닉네임",
                "newpassword1234",
                true
        );

        given(userRepository.findByUsername("test@naver.com")).willReturn(Optional.of(socialUser));
        given(userRepository.existsByNickname("다른닉네임")).willReturn(false);
        given(passwordEncoder.encode("newpassword1234")).willReturn("encoded-password");

        // when
        authService.signup(request);

        // then
        assertThat(socialUser.getPassword()).isEqualTo("encoded-password");
        verify(userRepository, times(1)).save(socialUser);
    }

    // ===== login =====

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

    // ===== reissue =====

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
    @DisplayName("폐기된 지 얼마 안 된 토큰(유예 구간 이내)이 재사용되면, 최신 후속 토큰을 찾아 자연스럽게 재발급된다")
    void reissue_success_withinGracePeriod_healsFromLatestDescendant() {
        // given: 새로고침 연타 등으로 이미 로테이션된 옛 토큰이 살짝 늦게(유예 구간 이내) 다시 들어온 상황.
        User user = createActiveUser();

        RefreshToken staleToken = createSavedRefreshToken(user);
        staleToken.setId(1L);
        staleToken.setRevokedAt(LocalDateTime.now().minusSeconds(2)); // 5초 유예 구간 이내
        staleToken.setReplacedByTokenId(2L);

        RefreshToken currentLiveToken = createSavedRefreshToken(user); // 그 사이 정상적으로 로테이션된 최신 토큰
        currentLiveToken.setId(2L);

        String rawRefreshToken = "stale-but-recent-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(staleToken));
        given(refreshTokenRepository.findById(2L)).willReturn(Optional.of(currentLiveToken));
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("healed-access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                .willReturn("healed-refresh-token");

        // when
        TokenResponse response = authService.reissue(rawRefreshToken);

        // then: 전체 로그아웃 대신, 최신 살아있는 토큰(currentLiveToken) 쪽에서 정상 로테이션이 일어난다.
        assertThat(response.getAccessToken()).isEqualTo("healed-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("healed-refresh-token");
        assertThat(currentLiveToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRevocationService, never()).revokeAllTokens(any());
    }

    @Test
    @DisplayName("유예 구간을 벗어난 재사용은 후속 토큰 체인이 있어도 진짜 재사용으로 간주해 전체 로그아웃시킨다")
    void reissue_fail_tokenReused_outsideGracePeriod_evenWithReplacementChain() {
        // given
        User user = createActiveUser();
        RefreshToken staleToken = createSavedRefreshToken(user);
        staleToken.setId(1L);
        staleToken.setRevokedAt(LocalDateTime.now().minusSeconds(30)); // 유예 구간(5초) 초과
        staleToken.setReplacedByTokenId(2L);

        String rawRefreshToken = "old-stale-token";
        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(staleToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));

        verify(refreshTokenRepository, never()).findById(any());
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

    // ===== logout =====

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

    // ===== resetPassword =====

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

    // ===== kakaoLogin =====

    @Test
    @DisplayName("이미 연동된 카카오 계정으로 로그인하면 기존 유저로 로그인 처리된다")
    void kakaoLogin_success_existingUser() {
        // given
        User user = createActiveUser();
        OauthAccount oauthAccount = new OauthAccount();
        oauthAccount.setUser(user);
        oauthAccount.setProvider("KAKAO");
        oauthAccount.setProviderId("123456");

        KakaoUserInfoResponse kakaoUserInfo = new KakaoUserInfoResponse();
        kakaoUserInfo.setId(123456L);

        given(kakaoApiClient.getAccessToken("valid-code")).willReturn("kakao-access-token");
        given(kakaoApiClient.getUserInfo("kakao-access-token")).willReturn(kakaoUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("KAKAO", "123456"))
                .willReturn(Optional.of(oauthAccount));
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.kakaoLogin("valid-code");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("처음 로그인하는 카카오 계정이면 자동 회원가입 후 로그인 처리된다")
    void kakaoLogin_success_newUser() {
        // given
        KakaoUserInfoResponse.Profile profile = new KakaoUserInfoResponse.Profile();
        profile.setNickname("카카오유저");
        KakaoUserInfoResponse.KakaoAccount kakaoAccount = new KakaoUserInfoResponse.KakaoAccount();
        kakaoAccount.setProfile(profile);
        KakaoUserInfoResponse kakaoUserInfo = new KakaoUserInfoResponse();
        kakaoUserInfo.setId(999999L);
        kakaoUserInfo.setKakao_account(kakaoAccount);

        given(kakaoApiClient.getAccessToken("new-code")).willReturn("kakao-access-token");
        given(kakaoApiClient.getUserInfo("kakao-access-token")).willReturn(kakaoUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("KAKAO", "999999"))
                .willReturn(Optional.empty());
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(jwtTokenProvider.generateAccessToken(any(), any(), any(), any()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(any())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.kakaoLogin("new-code");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("kakao_999999@kakao.local");
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.getNickname()).startsWith("카카오유저_");

        ArgumentCaptor<OauthAccount> oauthCaptor = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository, times(1)).save(oauthCaptor.capture());
        assertThat(oauthCaptor.getValue().getProvider()).isEqualTo("KAKAO");
        assertThat(oauthCaptor.getValue().getProviderId()).isEqualTo("999999");
    }

    @Test
    @DisplayName("카카오 토큰 교환이나 사용자 정보 조회가 실패하면 OAUTH_TOKEN_INVALID 예외가 발생한다")
    void kakaoLogin_fail_invalidToken() {
        // given
        given(kakaoApiClient.getAccessToken("bad-code"))
                .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        // when & then
        assertThatThrownBy(() -> authService.kakaoLogin("bad-code"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.OAUTH_TOKEN_INVALID));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("정지된 계정의 카카오 로그인이면 ACCOUNT_SUSPENDED 예외가 발생한다")
    void kakaoLogin_fail_accountSuspended() {
        // given
        User user = createActiveUser();
        user.setStatus(AccountStatus.SUSPENDED);
        OauthAccount oauthAccount = new OauthAccount();
        oauthAccount.setUser(user);

        KakaoUserInfoResponse kakaoUserInfo = new KakaoUserInfoResponse();
        kakaoUserInfo.setId(123456L);

        given(kakaoApiClient.getAccessToken("code")).willReturn("token");
        given(kakaoApiClient.getUserInfo("token")).willReturn(kakaoUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("KAKAO", "123456"))
                .willReturn(Optional.of(oauthAccount));

        // when & then
        assertThatThrownBy(() -> authService.kakaoLogin("code"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.ACCOUNT_SUSPENDED));
    }

    // ===== googleLogin =====

    @Test
    @DisplayName("이미 연동된 구글 계정으로 로그인하면 기존 유저로 로그인 처리된다")
    void googleLogin_success_existingLinkedUser() {
        // given
        User user = createActiveUser();
        OauthAccount oauthAccount = new OauthAccount();
        oauthAccount.setUser(user);
        oauthAccount.setProvider("GOOGLE");
        oauthAccount.setProviderId("google-id-123");

        GoogleUserInfoResponse googleUserInfo = new GoogleUserInfoResponse();
        googleUserInfo.setId("google-id-123");
        googleUserInfo.setEmail("test@naver.com");

        given(googleApiClient.getAccessToken("valid-code")).willReturn("google-access-token");
        given(googleApiClient.getUserInfo("google-access-token")).willReturn(googleUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("GOOGLE", "google-id-123"))
                .willReturn(Optional.of(oauthAccount));
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFestivalId()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.googleLogin("valid-code");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("이메일이 같은 기존 일반 가입 유저가 있으면 새 유저를 만들지 않고 OauthAccount만 연결한다 (자동 연동)")
    void googleLogin_success_autoLinkExistingUser() {
        // given
        User existingUser = createActiveUser();
        GoogleUserInfoResponse googleUserInfo = new GoogleUserInfoResponse();
        googleUserInfo.setId("google-id-999");
        googleUserInfo.setEmail("test@naver.com");

        given(googleApiClient.getAccessToken("code")).willReturn("token");
        given(googleApiClient.getUserInfo("token")).willReturn(googleUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("GOOGLE", "google-id-999"))
                .willReturn(Optional.empty());
        given(userRepository.findByUsername("test@naver.com")).willReturn(Optional.of(existingUser));
        given(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(any())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.googleLogin("code");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(userRepository, never()).save(any());

        ArgumentCaptor<OauthAccount> oauthCaptor = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository, times(1)).save(oauthCaptor.capture());
        assertThat(oauthCaptor.getValue().getUser()).isEqualTo(existingUser);
        assertThat(oauthCaptor.getValue().getProvider()).isEqualTo("GOOGLE");
        assertThat(oauthCaptor.getValue().getProviderId()).isEqualTo("google-id-999");
    }

    @Test
    @DisplayName("같은 이메일의 기존 유저가 없으면 완전히 새로운 유저를 자동 생성한다")
    void googleLogin_success_createNewUser() {
        // given
        GoogleUserInfoResponse googleUserInfo = new GoogleUserInfoResponse();
        googleUserInfo.setId("google-id-777");
        googleUserInfo.setEmail("newgoogle@gmail.com");
        googleUserInfo.setName("구글신규유저");

        given(googleApiClient.getAccessToken("code")).willReturn("token");
        given(googleApiClient.getUserInfo("token")).willReturn(googleUserInfo);
        given(oauthAccountRepository.findByProviderAndProviderId("GOOGLE", "google-id-777"))
                .willReturn(Optional.empty());
        given(userRepository.findByUsername("newgoogle@gmail.com")).willReturn(Optional.empty());
        given(userRepository.existsByNickname(any())).willReturn(false);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(any())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.googleLogin("code");

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("newgoogle@gmail.com");
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);

        ArgumentCaptor<OauthAccount> oauthCaptor = ArgumentCaptor.forClass(OauthAccount.class);
        verify(oauthAccountRepository, times(1)).save(oauthCaptor.capture());
        assertThat(oauthCaptor.getValue().getProvider()).isEqualTo("GOOGLE");
    }

    @Test
    @DisplayName("구글 토큰 교환이나 사용자 정보 조회가 실패하면 OAUTH_TOKEN_INVALID 예외가 발생한다")
    void googleLogin_fail_invalidToken() {
        // given
        given(googleApiClient.getAccessToken("bad-code"))
                .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        // when & then
        assertThatThrownBy(() -> authService.googleLogin("bad-code"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.OAUTH_TOKEN_INVALID));

        verify(userRepository, never()).save(any());
    }
}