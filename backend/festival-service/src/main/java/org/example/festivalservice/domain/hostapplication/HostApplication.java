package org.example.festivalservice.domain.hostapplication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "host_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HostApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HostApplicationStatus status;

    @Column(length = 1000)
    private String introduction;

    @Column(length = 255)
    private String contact;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public HostApplication(Long userId, String introduction, String contact) {
        this.userId = userId;
        this.introduction = introduction;
        this.contact = contact;
        this.status = HostApplicationStatus.PENDING;
    }

    //운영자 심사: 승인
    public void approve() {
        this.status = HostApplicationStatus.APPROVED;
    }

    //운영자 심사: 반려
    public void reject(String rejectReason) {
        this.status = HostApplicationStatus.REJECTED;
        this.rejectReason = rejectReason;
    }
}
