package org.example.authservice.auth.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String email;

    @Column(nullable = false,length = 6)
    private String code;

    @Column(name = "expires_at",nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "failed_attempts",nullable = false)
    private int failedAttempts = 0;  //인증코드를 몇번 틀렸는지 확인하는 필드 (무차별 대입 방지)

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;  //인증 성공 시각, 비밀번호 재설정은 인증 후 일정 시간 안에만 허용

    @Column(name = "verification_token_hash",length = 64)
    private String verificationTokenHash;  //인증 성공 때 발급한 토큰의 해시, 재설정 요청이 인증한 본인에게서 왔는지 확인

    @CreatedDate
    @Column(name="created_at",nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
