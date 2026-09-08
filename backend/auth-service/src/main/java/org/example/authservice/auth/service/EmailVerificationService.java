package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.entity.EmailVerification;
import org.example.authservice.auth.exception.EmailVerificationErrorCode;
import org.example.authservice.auth.repository.EmailVerificationRepository;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
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

    // 인증코드 검증
    @Transactional
    public void verifyCode(String email,String code){
        EmailVerification emailVerification = emailVerificationRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE));

        if(emailVerification.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new ApiException(EmailVerificationErrorCode.VERIFICATION_CODE_EXPIRED);
        }
        if(!emailVerification.getCode().equals(code)){
            throw new ApiException(EmailVerificationErrorCode.INVALID_VERIFICATION_CODE);
        }
        emailVerification.setVerified(true);
        emailVerificationRepository.save(emailVerification);
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

}
