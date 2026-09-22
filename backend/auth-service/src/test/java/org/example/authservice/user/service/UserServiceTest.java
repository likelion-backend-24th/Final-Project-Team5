package org.example.authservice.user.service;

import org.example.authservice.auth.entity.OauthAccount;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.auth.repository.OauthAccountRepository;
import org.example.authservice.auth.repository.RefreshTokenRepository;
import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.dto.InternalUserSummaryResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @org.junit.jupiter.api.BeforeEach
    void configureAccountPolicy() {
        org.springframework.test.util.ReflectionTestUtils.setField(userService, "accountAccessPolicy", new org.example.authservice.auth.service.AccountAccessPolicy());
    }

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    @InjectMocks
    private UserService userService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OauthAccountRepository oauthAccountRepository;

    @Test
    @DisplayName("내부 사용자 조회는 중복을 제거하고 200명까지만 기존 요약으로 반환한다")
    void findInternalUserSummaries_preservesLimitAndResponse() {
        User user = createActiveUser();
        user.setId(1L);
        List<Long> ids = LongStream.rangeClosed(1, 201).flatMap(id -> LongStream.of(id, id)).boxed().toList();
        List<Long> limited = LongStream.rangeClosed(1, 200).boxed().toList();
        given(userRepository.findAllById(limited)).willReturn(List.of(user));

        assertThat(userService.findInternalUserSummaries(ids)).containsExactly(InternalUserSummaryResponse.from(user));
        verify(userRepository).findAllById(limited);
    }

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

    @Test
    @DisplayName("정상적인 비밀번호 변경 시 성공하고, 기존 세션이 전부 무효화된다")
    void updatePassword_success() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("test1234", user.getPassword())).willReturn(true);
        given(passwordEncoder.encode("newpassword1234")).willReturn("encoded-new-password");

        // when
        userService.updatePassword(1L, "test1234", "newpassword1234", "newpassword1234");

        // then
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(userRepository, times(1)).save(user);
        verify(refreshTokenRevocationService, times(1)).revokeAllTokens(user);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 INVALID_CURRENT_PASSWORD 예외가 발생한다")
    void updatePassword_fail_invalidCurrentPassword() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpassword", user.getPassword())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.updatePassword(1L, "wrongpassword", "newpassword1234", "newpassword1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.INVALID_CURRENT_PASSWORD));

        verify(userRepository, never()).save(any());
        verify(refreshTokenRevocationService, never()).revokeAllTokens(any());
    }

    @Test
    @DisplayName("새 비밀번호와 확인 비밀번호가 다르면 PASSWORD_CONFIRM_MISMATCH 예외가 발생한다")
    void updatePassword_fail_confirmMismatch() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("test1234", user.getPassword())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updatePassword(1L, "test1234", "newpassword1234", "different1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.PASSWORD_CONFIRM_MISMATCH));

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("도우미 계정은 비밀번호를 변경할 수 없다 — 분실 시 주최자가 재발급해준다")
    void updatePassword_fail_helperAccount() {
        // given
        User helper = createActiveUser();
        helper.setRole(Role.HELPER);
        given(userRepository.findById(1L)).willReturn(Optional.of(helper));

        // when & then
        assertThatThrownBy(() -> userService.updatePassword(1L, "test1234", "newpassword1234", "newpassword1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.HELPER_PASSWORD_CHANGE_NOT_ALLOWED));
    }

    @Test
    @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외가 발생한다")
    void updatePassword_fail_userNotFound() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updatePassword(999L, "aaa", "bbb", "bbb"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("동의 문구가 맞으면 탈퇴 처리되고 이메일·소셜 연결이 풀리며 세션이 무효화된다")
    void withdrawAccount_success() {
        // given
        User user = createActiveUser();
        user.setId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.withdrawAccount(1L, "회원 탈퇴에 동의합니다");

        // then
        assertThat(user.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getNickname()).isEqualTo("탈퇴한사용자_1");
        //같은 이메일·같은 소셜 계정으로 다시 가입할 수 있어야 한다
        assertThat(user.getUsername()).isEqualTo("withdrawn_1@withdrawn.local");
        assertThat(user.getPassword()).isNull();
        verify(oauthAccountRepository, times(1)).deleteAllByUser_Id(1L);
        verify(userRepository, times(1)).save(user);
        verify(refreshTokenRevocationService, times(1)).revokeAllTokens(user);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("동의 문구 앞뒤 공백은 허용한다")
    void withdrawAccount_success_trimsConfirmation() {
        // given
        User user = createActiveUser();
        user.setId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.withdrawAccount(1L, "  회원 탈퇴에 동의합니다 ");

        // then
        assertThat(user.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("동의 문구가 다르면 WITHDRAW_CONFIRMATION_MISMATCH 예외가 발생하고 계정은 바뀌지 않는다")
    void withdrawAccount_fail_confirmationMismatch() {
        // when & then
        assertThatThrownBy(() -> userService.withdrawAccount(1L, "탈퇴합니다"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.WITHDRAW_CONFIRMATION_MISMATCH));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).deleteAllByUser_Id(any());
        verify(refreshTokenRevocationService, never()).revokeAllTokens(any());
    }

    @Test
    @DisplayName("존재하지 않는 userId면 USER_NOT_FOUND 예외가 발생한다")
    void withdrawAccount_fail_userNotFound() {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.withdrawAccount(999L, "회원 탈퇴에 동의합니다"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("카카오로 가입한 소셜 전용 계정(비밀번호 없음)은 비밀번호를 변경할 수 없다")
    void updatePassword_fail_socialOnlyUser() {
        // given
        User user = createActiveUser();
        user.setPassword(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> userService.updatePassword(1L, "anything", "newpassword1234", "newpassword1234"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.SOCIAL_USER_CANNOT_CHANGE_PASSWORD));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호 계정에 구글을 연동한 회원은 두 방식 모두 쓰므로 비밀번호를 계속 변경할 수 있다")
    void updatePassword_success_googleLinkedUserWithPassword() {
        // given
        User user = createActiveUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("test1234", user.getPassword())).willReturn(true);
        given(passwordEncoder.encode("newpassword1234")).willReturn("encoded-new-password");

        // when
        userService.updatePassword(1L, "test1234", "newpassword1234", "newpassword1234");

        // then
        assertThat(user.getPassword()).isEqualTo("encoded-new-password");
        verify(refreshTokenRevocationService, times(1)).revokeAllTokens(user);
    }

    @Test
    @DisplayName("내 정보 조회 응답에 연결된 소셜 제공자와 비밀번호 유무가 포함된다")
    void getMyInfo_includesSocialProviders() {
        // given
        User user = createActiveUser();
        user.setId(1L);
        user.setPassword(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        OauthAccount kakao = new OauthAccount();
        kakao.setProvider("KAKAO");
        given(oauthAccountRepository.findAllByUser_Id(1L)).willReturn(List.of(kakao));

        // when
        UserResponse response = userService.getMyInfo(1L);

        // then
        assertThat(response.getSocialProviders()).containsExactly("KAKAO");
        assertThat(response.isHasPassword()).isFalse();
    }

    @Test
    @DisplayName("소셜 가입 회원이 약관에 동의하고 이름·닉네임을 정하면 프로필 설정이 완료된다")
    void completeProfileSetup_success() {
        // given
        User user = createActiveUser();
        user.setTermsAgreeAt(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByNickname("새닉네임")).willReturn(false);

        // when
        userService.completeProfileSetup(1L, "홍길동", "새닉네임", true);

        // then
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getNickname()).isEqualTo("새닉네임");
        assertThat(user.getTermsAgreeAt()).isNotNull();
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("약관에 동의하지 않으면 프로필 설정이 거부된다")
    void completeProfileSetup_fail_termsNotAgreed() {
        assertThatThrownBy(() -> userService.completeProfileSetup(1L, "홍길동", "새닉네임", false))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.TERMS_NOT_AGREED));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("비밀번호가 있는 일반 회원은 약관 동의 시각이 비어 있어도 소셜 최초 가입자가 아니므로 profileSetupRequired가 false다")
    void getMyInfo_profileSetupNotRequired_forPasswordUserEvenIfTermsNull() {
        User user = createActiveUser();
        user.setId(1L);
        user.setTermsAgreeAt(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getMyInfo(1L);

        assertThat(response.isProfileSetupRequired()).isFalse();
    }

    @Test
    @DisplayName("비밀번호가 없고(소셜 전용) 약관 동의 시각도 비어 있는 회원은 profileSetupRequired가 true다")
    void getMyInfo_profileSetupRequired_whenSocialOnlyAndTermsNotAgreed() {
        User user = createActiveUser();
        user.setId(1L);
        user.setPassword(null);
        user.setTermsAgreeAt(null);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserResponse response = userService.getMyInfo(1L);

        assertThat(response.isProfileSetupRequired()).isTrue();
    }
}
