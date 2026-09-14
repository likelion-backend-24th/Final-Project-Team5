package org.example.paymentservice.domain.cancellation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "festival_refund_batches", uniqueConstraints = @UniqueConstraint(columnNames = "festival_id"))
public class FestivalRefundBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    private Long initiatedBy;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(30)")
    private FestivalRefundBatchStatus status = FestivalRefundBatchStatus.RUNNING;

    private Instant createdAt = Instant.now();

    private Instant completedAt;

    private int totalCount;

    private int succeededCount;

    private int failedCount;

    public FestivalRefundBatch(Long festivalId, Long actor, String reason) {
        this.festivalId = festivalId;
        initiatedBy = actor;
        this.reason = reason;
    }

    // 기존 배치 조회 호출자의 문자열 상태 계약은 보존한다.
    public String getStatus() {
        return status.name();
    }

    public void progress(int total, int succeeded, int failed) {
        totalCount = total;
        succeededCount = succeeded;
        failedCount = failed;
    }

    public void complete() {
        status = FestivalRefundBatchStatus.SUCCEEDED;
        completedAt = Instant.now();
    }
}
