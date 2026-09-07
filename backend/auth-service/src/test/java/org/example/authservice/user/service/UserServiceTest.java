package org.example.authservice.user.service;

import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.UserResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.exception.UserErrorCode;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("존재하는 userId로 조회하면 내 정보를 정확히 반환한다")
    void getMyInfo_success() {
        // given
        User user = createActiveUser();
        user.setId(1L);
        user.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getMyInfo(1L);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("test@naver.com");
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getNickname()).isEqualTo("안양개발자");
        assertThat(response.getRole()).isEqualTo(Role.USER);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 10, 0));
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 USER_NOT_FOUND 예외가 발생한다")
    void getMyInfo_fail_userNotFound() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(999L))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("정상적인 닉네임으로 변경하면 성공한다")
    void updateNickname_success() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("새닉네임")).willReturn(false);

        // when
        userService.updateNickname(1L, "새닉네임");

        // then
        assertThat(user.getNickname()).isEqualTo("새닉네임");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("본인의 현재 닉네임과 같은 값으로 요청하면 예외 없이 조용히 종료한다")
    void updateNickname_sameAsCurrent_doesNothing() {
        // given
        User user = createActiveUser(); // createActiveUser()의 nickname 기본값 사용
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.updateNickname(1L, user.getNickname());

        // then
        verify(userRepository, never()).existsByNickname(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 유저가 이미 쓰는 닉네임이면 DUPLICATE_NICKNAME 예외가 발생한다")
    void updateNickname_fail_duplicateNickname() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(1L, "중복닉네임"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외가 발생한다")
    void updateNickname_fail_userNotFound() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(999L, "아무닉네임"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
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