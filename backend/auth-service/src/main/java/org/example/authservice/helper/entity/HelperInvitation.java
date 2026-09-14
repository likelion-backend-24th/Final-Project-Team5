package org.example.authservice.helper.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.example.authservice.user.entity.User;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "helper_invitations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_helper_invitation_email_festival", columnNames = {"festival_id", "normalized_email"})
})
@Getter
@Setter
public class HelperInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "helper_user_id", nullable = false, unique = true)
    private User helperUser;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(nullable = false)
    private String festivalName;

    @Column(nullable = false)
    private LocalDateTime festivalStartAt;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENDING;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime sentAt;

    private LocalDateTime lastSentAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime revokedAt;

    @Column(nullable = false)
    private int resendCount;

    @ElementCollection
    @CollectionTable(name = "helper_invitation_sends", joinColumns = @JoinColumn(name = "invitation_id"))
    @Column(name = "attempted_at", nullable = false)
    private List<LocalDateTime> sendAttempts = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        ACCEPTED,
        REVOKED
    }

    public enum DeliveryStatus {
        SENDING,
        SENT,
        SEND_FAILED
    }
}
