package org.example.paymentservice.domain.settlement;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Getter @NoArgsConstructor
@Table(name = "settlement_audit_logs", uniqueConstraints = @UniqueConstraint(columnNames = "command_key"))
public class SettlementAuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long settlementId;
    private String action;
    private String previousStatus;
    private String nextStatus;
    private Long actorUserId;
    @Column(length = 1000) private String memo;
    @Column(name = "command_key", length = 100) private String commandKey;
    private String commandFingerprint;
    private Instant createdAt = Instant.now();
    public SettlementAuditLog(Settlement settlement, String action, SettlementStatus previous,
                              Long actor, String memo, String key, String fingerprint) {
        settlementId = settlement.getId(); this.action = action; previousStatus = previous.name();
        nextStatus = settlement.getStatus().name(); actorUserId = actor; this.memo = memo;
        commandKey = key; commandFingerprint = fingerprint;
    }
}
