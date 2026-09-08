package org.example.authservice.auth.service;

import org.example.authservice.auth.entity.EmailVerification;
import org.example.authservice.auth.exception.EmailVerificationErrorCode;
import org.example.authservice.auth.repository.EmailVerificationRepository;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository emailVerificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Test
    @DisplayName("인증코드를 생성하고 발송한다")
    void sendCode_success() {
        // given
        String email = "test@naver.com";
        given(emailService.generateCode()).willReturn("123456");

        // when
        emailVerificationService.sendCode(email);

        // then
        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository, times(1)).save(captor.capture());

        EmailVerification saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getCode()).isEqualTo("123456");
        assertThat(saved.isVerified()).isFalse();

        verify(emailService, times(1)).sendVerificationCode(email, "123456");
    }

    @Test
    @DisplayName("정확한 코드로 검증하면 verified가 true로 바뀐다")
    void verifyCode_success() {
        // given
        String email = "test@naver.com";
        EmailVerification verification = createVerification(email, "123456", LocalDateTime.now().plusMinutes(5));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(verification));

        // when
        emailVerificationService.verifyCode(email, "123456");

        // then
        assertThat(verification.isVerified()).isTrue();
        verify(emailVerificationRepository, times(1)).save(verification);
    }

    @Test
    @DisplayName("발송 이력이 없는 이메일이면 INVALID_VERIFICATION_CODE 예외가 발생한다")
    void verifyCode_fail_noRecord() {
        // given
        String email = "notfound@naver.com";
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE));
    }

    @Test
    @DisplayName("코드가 만료되었으면 VERIFICATION_CODE_EXPIRED 예외가 발생한다")
    void verifyCode_fail_expired() {
        // given
        String email = "test@naver.com";
        EmailVerification verification = createVerification(email, "123456", LocalDateTime.now().minusMinutes(1));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(verification));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.VERIFICATION_CODE_EXPIRED));
    }

    @Test
    @DisplayName("코드가 일치하지 않으면 INVALID_VERIFICATION_CODE 예외가 발생한다")
    void verifyCode_fail_mismatch() {
        // given
        String email = "test@naver.com";
        EmailVerification verification = createVerification(email, "123456", LocalDateTime.now().plusMinutes(5));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(verification));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.verifyCode(email, "000000"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE));
    }

    @Test
    @DisplayName("인증 완료된 상태면 checkVerified가 통과하고 레코드를 삭제한다")
    void checkVerified_success_deletesRecord() {
        // given
        String email = "test@naver.com";
        EmailVerification verification = createVerification(email, "123456", LocalDateTime.now().plusMinutes(5));
        verification.setVerified(true);
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(verification));

        // when
        emailVerificationService.checkVerified(email);

        // then
        verify(emailVerificationRepository, times(1)).delete(verification);
    }

    @Test
    @DisplayName("인증 완료되지 않았으면 EMAIL_NOT_VERIFIED 예외가 발생한다")
    void checkVerified_fail_notVerified() {
        // given
        String email = "test@naver.com";
        EmailVerification verification = createVerification(email, "123456", LocalDateTime.now().plusMinutes(5));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(verification));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.checkVerified(email))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED));

        verify(emailVerificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("발송 이력 자체가 없으면 EMAIL_NOT_VERIFIED 예외가 발생한다")
    void checkVerified_fail_noRecord() {
        // given
        String email = "notfound@naver.com";
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> emailVerificationService.checkVerified(email))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED));
    }

    private EmailVerification createVerification(String email, String code, LocalDateTime expiresAt) {
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setCode(code);
        verification.setExpiresAt(expiresAt);
        verification.setVerified(false);
        return verification;
    }

    @Test
    @DisplayName("30초 이내 재발송 요청이면 TOO_MANY_REQUESTS_COOLDOWN 예외가 발생한다")
    void sendCode_fail_cooldown() {
        // given
        String email = "test@naver.com";
        EmailVerification recentSend = createVerification(email, "111111", LocalDateTime.now().plusMinutes(5));
        setCreatedAt(recentSend, LocalDateTime.now().minusSeconds(10)); // 10초 전에 발송됨
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(recentSend));

        // when & then
        assertThatThrownBy(() -> emailVerificationService.sendCode(email))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.TOO_MANY_REQUESTS_COOLDOWN));

        verify(emailVerificationRepository, never()).save(any());
        verify(emailService, never()).sendVerificationCode(any(), any());
    }

    @Test
    @DisplayName("쿨다운(30초)은 지났지만 10분 내 5회를 초과했으면 TOO_MANY_REQUESTS_LIMIT 예외가 발생한다")
    void sendCode_fail_rateLimitExceeded() {
        // given
        String email = "test@naver.com";
        EmailVerification recentSend = createVerification(email, "111111", LocalDateTime.now().plusMinutes(5));
        setCreatedAt(recentSend, LocalDateTime.now().minusSeconds(40)); // 쿨다운은 지남
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(recentSend));
        given(emailVerificationRepository.countByEmailAndCreatedAtAfter(any(), any()))
                .willReturn(5L); // 이미 10분 내 5회 발송함

        // when & then
        assertThatThrownBy(() -> emailVerificationService.sendCode(email))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode())
                        .isEqualTo(EmailVerificationErrorCode.TOO_MANY_REQUESTS_LIMIT));

        verify(emailVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("쿨다운도 지나고 횟수 제한도 안 걸렸으면 정상적으로 발송된다")
    void sendCode_success_afterCooldownAndUnderLimit() {
        // given
        String email = "test@naver.com";
        EmailVerification recentSend = createVerification(email, "111111", LocalDateTime.now().plusMinutes(5));
        setCreatedAt(recentSend, LocalDateTime.now().minusSeconds(40));
        given(emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email))
                .willReturn(Optional.of(recentSend));
        given(emailVerificationRepository.countByEmailAndCreatedAtAfter(any(), any()))
                .willReturn(2L); // 아직 5회 미만
        given(emailService.generateCode()).willReturn("222222");

        // when
        emailVerificationService.sendCode(email);

        // then
        verify(emailVerificationRepository, times(1)).save(any(EmailVerification.class));
        verify(emailService, times(1)).sendVerificationCode(email, "222222");
    }

    // 헬퍼: @CreationTimestamp가 자동 생성하는 createdAt 필드를 테스트에서 강제로 세팅
    private void setCreatedAt(EmailVerification verification, LocalDateTime createdAt) {
        org.springframework.test.util.ReflectionTestUtils.setField(verification, "createdAt", createdAt);
    }
}