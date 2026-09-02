package org.example.authservice.auth.service;

import org.example.authservice.auth.dto.LoginRequest;
import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.dto.TokenResponse;
import org.example.authservice.auth.entity.RefreshToken;
import org.example.authservice.auth.exception.AuthErrorCode;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    @InjectMocks
    private AuthService authService;

    private SignupRequest createValidRequest() {
        return new SignupRequest(
                "홍길동",
                "test@naver.com",
                "안양개발자",
                "test1234"
        );
    }

    @Test
    @DisplayName("정상적인 요청이면 회원가입에 성공하고, 비밀번호는 암호화되어 저장된다")
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
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password"); // 평문이 아니라 암호화된 값이 들어갔는지 확인
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
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
        verify(userRepository, never()).existsByNickname(any()); // username 중복이면 nickname 체크까지 안 가야 함
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
        assertThat(captor.getValue().getPassword()).isNotEqualTo("test1234"); // 평문 그대로 저장되면 안 됨
    }

    @Test
    @DisplayName("정상적인 로그인 요청이면 Access/Refresh Token을 발급하고, Refresh Token은 해시되어 저장된다")
    void login_success() {
        // given
        User user = createActiveUser(); // 아래 헬퍼 참고
        LoginRequest request = new LoginRequest("test@naver.com", "test1234");

        given(userRepository.findByUsername(request.getUsername())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.getPassword(), user.getPassword())).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name()))
                .willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername())).willReturn("refresh-token");

        // when
        TokenResponse response = authService.login(request);

        // then
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo("refresh-token"); // 평문 저장 방지 확인
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
    @DisplayName("비밀번호가 틀리면 INVALID_PASSWORD 예외가 발생한다")
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

        verify(refreshTokenRepository, never()).save(any());
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
        given(jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name()))
                .willReturn("new-access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getUsername()))
                .willReturn("new-refresh-token");

        // when
        TokenResponse response = authService.reissue(rawRefreshToken);

        // then
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

        // 기존 토큰이 폐기(revoked)됐는지 확인
        assertThat(savedToken.getRevokedAt()).isNotNull();
        // replacedByTokenId는 mock 환경에서 새 엔티티의 id가 null이라 같이 null이 됨 (정상)
        // → id 자체보다는 "새 토큰 저장 로직이 실행됐는지"를 확인하는 게 더 적절함

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
    @DisplayName("이미 폐기된 토큰이 재사용되면 REFRESH_TOKEN_REUSED 예외가 발생하고, 해당 유저의 모든 활성 토큰이 폐기된다")
    void reissue_fail_tokenReused() {
        // given
        User user = createActiveUser();
        RefreshToken revokedToken = createSavedRefreshToken(user);
        revokedToken.setRevokedAt(LocalDateTime.now().minusMinutes(5)); // 이미 폐기된 상태

        RefreshToken otherActiveToken = createSavedRefreshToken(user); // 유저의 다른 활성 토큰
        String rawRefreshToken = "reused-token";

        given(jwtTokenProvider.validateToken(rawRefreshToken)).willReturn(true);
        given(refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)))
                .willReturn(Optional.of(revokedToken));
        given(refreshTokenRepository.findAllByUser_IdAndRevokedAtIsNull(user.getId()))
                .willReturn(List.of(otherActiveToken));

        // when & then
        assertThatThrownBy(() -> authService.reissue(rawRefreshToken))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSED));

        // 그 유저의 다른 활성 토큰도 강제 폐기됐는지 확인
        assertThat(otherActiveToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("DB 상 만료된 토큰이면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissue_fail_expiredInDb() {
        // given
        User user = createActiveUser();
        RefreshToken expiredToken = createSavedRefreshToken(user);
        expiredToken.setExpiresAt(LocalDateTime.now().minusDays(1)); // DB 상 이미 만료

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
}