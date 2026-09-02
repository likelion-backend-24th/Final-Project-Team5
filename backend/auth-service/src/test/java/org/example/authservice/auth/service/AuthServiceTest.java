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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}