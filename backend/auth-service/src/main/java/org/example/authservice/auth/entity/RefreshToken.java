package org.example.authservice.auth.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.authservice.user.entity.User;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_token_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 평문 토큰이 아니라 해시값 저장
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 폐기 시점 (null이면 아직 살아있는 토큰)
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // 이 토큰이 재발급되면서 교체된 새 토큰의 id
    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}