package org.example.paymentservice.domain.cancellation;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Getter @NoArgsConstructor
@Table(name = "festival_refund_batches", uniqueConstraints = @UniqueConstraint(columnNames = "festival_id"))
public class FestivalRefundBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "festival_id", nullable = false) private Long festivalId;
    private Long initiatedBy;
    @Column(length = 500) private String reason;
    private String status = "RUNNING";
    private Instant createdAt = Instant.now();
    private Instant completedAt;
    private int totalCount;
    private int succeededCount;
    private int failedCount;
    public FestivalRefundBatch(Long festivalId, Long actor, String reason) {
        this.festivalId = festivalId; initiatedBy = actor; this.reason = reason;
    }
    public void progress(int total, int succeeded, int failed) { totalCount = total; succeededCount = succeeded; failedCount = failed; }
    public void complete() { status = "SUCCEEDED"; completedAt = Instant.now(); }
}
