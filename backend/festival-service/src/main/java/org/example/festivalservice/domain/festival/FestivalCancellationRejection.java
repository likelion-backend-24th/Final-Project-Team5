package org.example.festivalservice.domain.festival;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 행사 취소 요청 반려 이력. 반려하면 Festival의 취소 요청 기록(사유·요청자)이 지워지므로,
 * 지우기 직전의 값을 여기에 복사해 운영자가 "언제 누가 무엇을 반려했는지" 볼 수 있게 한다.
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "festival_cancellation_rejections")
public class FestivalCancellationRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //반려된 페스티벌 id (같은 페스티벌이 여러 번 반려될 수 있어 unique 아님)
    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    //반려 시점의 페스티벌 이름 스냅샷
    @Column(name = "festival_name")
    private String festivalName;

    @Column(name = "host_user_id")
    private Long hostUserId;

    //취소를 요청한 주최자
    @Column(name = "requested_by_user_id")
    private Long requestedByUserId;

    //주최자가 적은 취소 사유
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    //반려한 운영자
    @Column(name = "rejected_by_user_id", nullable = false)
    private Long rejectedByUserId;

    @CreationTimestamp
    @Column(name = "rejected_at", nullable = false, updatable = false)
    private LocalDateTime rejectedAt;
}