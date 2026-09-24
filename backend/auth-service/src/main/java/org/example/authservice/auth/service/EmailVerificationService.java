package org.example.authservice.auth.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.entity.EmailVerification;
import org.example.authservice.auth.exception.EmailVerificationErrorCode;
import org.example.authservice.auth.repository.EmailVerificationRepository;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom secureRandom = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    //인증코드 발송
    @Transactional
    public void sendCode(String email){
        // 재발송 쿨다운 30초, 횟수제한
        checkRateLimit(email);

        String code = emailService.generateCode(); //인증코드 6자리

        EmailVerification emailVerification = new EmailVerification();
        emailVerification.setEmail(email);
        emailVerification.setCode(code);
        emailVerification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        emailVerification.setVerified(false);

        emailVerificationRepository.save(emailVerification);

        emailService.sendVerificationCode(email,code);
    }

    // 인증코드 검증 (성공하면 비밀번호 재설정에 쓸 인증 토큰을 돌려줌)
    // 틀린 횟수는 예외를 던진 뒤에도 남아 있어야 하므로 ApiException으로는 롤백하지 않음 (롤백되면 횟수 제한이 무의미해짐)
    @Transactional(noRollbackFor = ApiException.class)
    public String verifyCode(String email,String code){
        EmailVerification emailVerification = emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE));
        // 같은 코드를 동시에 여러번 확인해도 틀린 횟수를 정확히 세도록 이 기록을 잠그고 최신 값으로 다시 읽음
        entityManager.refresh(emailVerification, LockModeType.PESSIMISTIC_WRITE);

        // 5번 틀린 코드는 더 이상 확인하지 않음, 인증코드를 새로 받아야함 (6자리 코드 무차별 대입 방지)
        if(emailVerification.getFailedAttempts() >= 5){
            throw new ApiException(EmailVerificationErrorCode.TOO_MANY_VERIFY_ATTEMPTS);
        }
        if(emailVerification.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new ApiException(EmailVerificationErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if(!emailVerification.getCode().equals(code)){
            //코드 틀릴 때마다 실패횟수 1씩 증가
            emailVerification.setFailedAttempts(emailVerification.getFailedAttempts() + 1);
            emailVerificationRepository.save(emailVerification);
            throw new ApiException(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE);
        }
        // 인증한 본인만 받는 토큰을 발급하고 DB에는 해시만 저장 (비밀번호 재설정 때 이 토큰까지 맞아야 통과)
        String verificationToken = generateVerificationToken();
        emailVerification.setVerified(true);
        emailVerification.setVerifiedAt(LocalDateTime.now());
        emailVerification.setVerificationTokenHash(TokenSessionService.hashToken(verificationToken));
        emailVerificationRepository.save(emailVerification);
        return verificationToken;
    }

    //이메일 인증 완료여부 확인 메서드
    @Transactional
    public void checkVerified(String email){
        EmailVerification emailVerification = emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED));

        if(!emailVerification.isVerified()){
            throw new ApiException(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED);
        }
        // 회원가입때 인증받으면 한번의 인증으로 계속 인증이 필요없어짐 비밀번호 재설정하기위하여 삭제해서 재인증을 유도
        emailVerificationRepository.delete(emailVerification);
    }

    //비밀번호 재설정 전용 인증 확인 메서드
    //이메일만 알면 남이 인증해 둔 기록으로 재설정할 수 있으므로, 인증 성공 때 발급한 토큰까지 맞아야 통과
    @Transactional
    public void checkVerifiedForReset(String email,String verificationToken){
        EmailVerification emailVerification = emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED));

        boolean tokenMatches = emailVerification.getVerificationTokenHash() != null
                && emailVerification.getVerificationTokenHash().equals(TokenSessionService.hashToken(verificationToken));
        // 인증 후 10분이 지났으면 다시 인증받아야함
        boolean withinResetWindow = emailVerification.getVerifiedAt() != null
                && emailVerification.getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(10));
        if(!emailVerification.isVerified() || !tokenMatches || !withinResetWindow){
            throw new ApiException(EmailVerificationErrorCode.EMAIL_NOT_VERIFIED);
        }
        // 한 번 쓴 인증은 삭제해서 같은 토큰으로 다시 재설정하지 못하게 함
        emailVerificationRepository.delete(emailVerification);
    }

    // 재발송 쿨다운(30초), 횟수 제한(10분 5회)
    public void checkRateLimit(String email) {
        emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .ifPresent(last -> {
                    if (last.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
                        throw new ApiException(EmailVerificationErrorCode.TOO_MANY_REQUESTS_COOLDOWN);
                    }
                });
        long count = emailVerificationRepository.countByEmailAndCreatedAtAfter(email, LocalDateTime.now().minusMinutes(10));

        if (count >= 5) {
            throw new ApiException(EmailVerificationErrorCode.TOO_MANY_REQUESTS_LIMIT);
        }
    }

    // 추측할 수 없는 32바이트 난수 토큰 (도우미 초대 토큰과 같은 방식)
    private String generateVerificationToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
