package org.example.authservice.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Festival-Service의 grantOrganizerRole(PUT /internal/v1/roles) 요청을 applicationId 기준으로
 * 멱등 처리하기 위한 기록. 같은 applicationId가 이미 있으면 Role을 다시 부여하지 않고 기존 처리 결과로 간주한다.
 */
@Entity
@Table(name = "role_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String role;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    public RoleGrant(Long applicationId, Long userId, String role) {
        this.applicationId = applicationId;
        this.userId = userId;
        this.role = role;
    }
}
