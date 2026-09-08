package org.example.authservice.helper.service;

import org.example.authservice.auth.service.RefreshTokenRevocationService;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.CreateHelperAccountRequest;
import org.example.authservice.helper.dto.HelperAccountCredentialResponse;
import org.example.authservice.helper.dto.HelperAccountSummaryResponse;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;
import org.example.authservice.user.entity.User;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("도우미 계정 발급·재발급·회수")
class HelperAccountServiceTest {

    private static final Long FESTIVAL_ID = 100L;
    private static final Long OTHER_FESTIVAL_ID = 200L;
    private static final LocalDateTime FESTIVAL_END_AT = LocalDateTime.now().plusDays(3);

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    private HelperAccountService helperAccountService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        helperAccountService = new HelperAccountService(
                userRepository, passwordEncoder, new HelperCredentialGenerator(), refreshTokenRevocationService);
    }

    @Test
    @DisplayName("발급하면 HELPER 역할과 담당 페스티벌이 지정된 계정이 만들어지고, 평문 비밀번호는 응답에만 담긴다")
    void createsHelperAccountBoundToFestival() {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByNickname(anyString())).willReturn(false);

        HelperAccountCredentialResponse response = helperAccountService.createHelperAccount(
                new CreateHelperAccountRequest(FESTIVAL_ID, FESTIVAL_END_AT));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo(Role.HELPER);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getFestivalId()).isEqualTo(FESTIVAL_ID);
        assertThat(saved.getFestivalEndAt()).isEqualTo(FESTIVAL_END_AT);
        //기존 로그인 화면이 이메일 형식을 요구하므로 아이디도 이메일 모양이어야 한다.
        assertThat(saved.getUsername()).contains("@");
        //비밀번호는 해시로만 저장되고, 평문은 응답으로만 나간다.
        assertThat(saved.getPassword()).isNotEqualTo(response.password());
        assertThat(passwordEncoder.matches(response.password(), saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("초기 비밀번호는 가입 최소 길이(8자)를 만족한다")
    void generatesPasswordSatisfyingMinimumLength() {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByNickname(anyString())).willReturn(false);

        HelperAccountCredentialResponse response = helperAccountService.createHelperAccount(
                new CreateHelperAccountRequest(FESTIVAL_ID, FESTIVAL_END_AT));

        assertThat(response.password()).hasSize(8);
    }

    @Test
    @DisplayName("호출할 때마다 서로 다른 계정이 발급된다")
    void createsDistinctAccountPerCall() {
        given(userRepository.existsByUsername(anyString())).willReturn(false);
        given(userRepository.existsByNickname(anyString())).willReturn(false);

        HelperAccountCredentialResponse first = helperAccountService.createHelperAccount(
                new CreateHelperAccountRequest(FESTIVAL_ID, FESTIVAL_END_AT));
        HelperAccountCredentialResponse second = helperAccountService.createHelperAccount(
                new CreateHelperAccountRequest(FESTIVAL_ID, FESTIVAL_END_AT));

        assertThat(first.username()).isNotEqualTo(second.username());
        assertThat(first.password()).isNotEqualTo(second.password());
    }

    @Test
    @DisplayName("재발급하면 비밀번호만 새로 만들고 기존 세션은 무효화한다")
    void reissuesPasswordAndRevokesSessions() {
        User helper = helperAccount(1L, FESTIVAL_ID);
        String originalHash = helper.getPassword();
        given(userRepository.findById(1L)).willReturn(Optional.of(helper));

        HelperAccountCredentialResponse response = helperAccountService.reissuePassword(FESTIVAL_ID, 1L);

        assertThat(helper.getPassword()).isNotEqualTo(originalHash);
        assertThat(passwordEncoder.matches(response.password(), helper.getPassword())).isTrue();
        //계정 자체는 그대로 유지된다.
        assertThat(response.username()).isEqualTo(helper.getUsername());
        verify(refreshTokenRevocationService).revokeAllTokens(helper);
    }

    @Test
    @DisplayName("다른 페스티벌의 도우미 계정은 재발급할 수 없다")
    void rejectsReissueForOtherFestivalHelper() {
        given(userRepository.findById(1L)).willReturn(Optional.of(helperAccount(1L, OTHER_FESTIVAL_ID)));

        assertThatThrownBy(() -> helperAccountService.reissuePassword(FESTIVAL_ID, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("해당 페스티벌의 도우미 계정이 아닙니다");
    }

    @Test
    @DisplayName("일반 회원 계정은 도우미 재발급 대상이 아니다")
    void rejectsReissueForNonHelperUser() {
        User normalUser = helperAccount(1L, null);
        normalUser.setRole(Role.USER);
        given(userRepository.findById(1L)).willReturn(Optional.of(normalUser));

        assertThatThrownBy(() -> helperAccountService.reissuePassword(FESTIVAL_ID, 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("존재하지 않는 도우미 계정");
    }

    @Test
    @DisplayName("목록 조회는 개수와 아이디만 돌려주고 비밀번호는 담지 않는다")
    void listsHelperAccountsWithoutPasswords() {
        given(userRepository.findByRoleAndFestivalIdAndStatus(Role.HELPER, FESTIVAL_ID, AccountStatus.ACTIVE))
                .willReturn(List.of(helperAccount(1L, FESTIVAL_ID), helperAccount(2L, FESTIVAL_ID)));

        HelperAccountSummaryResponse response = helperAccountService.listHelperAccounts(FESTIVAL_ID);

        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.helpers()).hasSize(2);
        assertThat(response.helpers().get(0).username()).isNotBlank();
    }

    @Test
    @DisplayName("페스티벌 종료 후 유예시간이 지난 계정은 탈퇴 처리되고 세션도 끊긴다")
    void withdrawsExpiredHelperAccounts() {
        User expired = helperAccount(1L, FESTIVAL_ID);
        LocalDateTime revokeThreshold = LocalDateTime.now().minusHours(24);
        given(userRepository.findByRoleAndStatusAndFestivalEndAtBefore(
                Role.HELPER, AccountStatus.ACTIVE, revokeThreshold)).willReturn(List.of(expired));

        int withdrawnCount = helperAccountService.withdrawExpiredHelperAccounts(revokeThreshold);

        assertThat(withdrawnCount).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(AccountStatus.WITHDRAWN);
        assertThat(expired.getWithdrawnAt()).isNotNull();
        verify(refreshTokenRevocationService).revokeAllTokens(expired);
    }

    @Test
    @DisplayName("회수 대상이 없으면 아무 계정도 건드리지 않는다")
    void doesNothingWhenNoExpiredHelperAccounts() {
        LocalDateTime revokeThreshold = LocalDateTime.now().minusHours(24);
        given(userRepository.findByRoleAndStatusAndFestivalEndAtBefore(
                Role.HELPER, AccountStatus.ACTIVE, revokeThreshold)).willReturn(List.of());

        assertThat(helperAccountService.withdrawExpiredHelperAccounts(revokeThreshold)).isZero();
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    private User helperAccount(Long id, Long festivalId) {
        User helper = new User();
        helper.setId(id);
        helper.setName("현장 도우미");
        helper.setUsername("helper-" + id + "@helper.local");
        helper.setNickname("도우미-" + id);
        helper.setPassword(passwordEncoder.encode("origin12"));
        helper.setRole(Role.HELPER);
        helper.setStatus(AccountStatus.ACTIVE);
        helper.setFestivalId(festivalId);
        helper.setFestivalEndAt(FESTIVAL_END_AT);
        helper.setCreatedAt(LocalDateTime.now());
        return helper;
    }
}
