package org.example.authservice.auth.repository;

import org.example.authservice.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification,Long> {
    // 이메일로 가장 최근 인증 기록 조회,   이메일을 계속 보내면 이메일이 계속쌓인는데 제일 최근 이메일로 인증받으려고!!
    Optional<EmailVerification> findTopByEmailOrderByCreatedAtDesc(String email);
    // 이 시각 이후에 생성된 기록을 몇 개인지 갯수 파악
    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);

}
