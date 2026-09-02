package org.example.authservice.auth.service;

import org.example.authservice.auth.dto.SignupRequest;
import org.example.authservice.auth.exception.AuthErrorCode;
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
}