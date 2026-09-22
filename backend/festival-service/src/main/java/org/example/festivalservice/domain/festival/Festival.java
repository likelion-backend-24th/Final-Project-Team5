package org.example.festivalservice.domain.festival;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "festivals")
public class Festival {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //주최자(Host) id
    @Column(name = "host_user_id")
    private Long hostUserId;

    //페스티벌 이름
    private String name;
    //페스티벌 설명
    private String description;
    //개최 일자 및 시각
    @Column(name = "start_at")
    private LocalDateTime startAt;
    //페스티벌 종료 일자 및 시각
    @Column(name = "end_at")
    private LocalDateTime endAt;
    //개최 장소 — 행정구역(시/도) 드롭다운 + 상세주소(도로명 등) 텍스트로 나눠 받는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "region", columnDefinition = "VARCHAR(20)")
    private FestivalRegion region;

    @Column(name = "location_detail")
    private String locationDetail;

    //입장 시작 시간(몇 시부터 입장 가능한지, 날짜 아닌 시각만). QR 체크인 로직에서는 쓰지 않고
    //구매자에게 안내만 하는 참고용 값이다.
    @Column(name = "entry_start_time")
    private LocalTime entryStartTime;

    //운영 시간(구매자 확인용 참고 정보). 마찬가지로 QR 체크인 검증에는 관여하지 않는다.
    @Column(name = "operating_start_time")
    private LocalTime operatingStartTime;

    @Column(name = "operating_end_time")
    private LocalTime operatingEndTime;


    //columnDefinition을 명시하지 않으면 Hibernate가 MySQL 네이티브 ENUM(...) 컬럼을 생성해,
    //Java enum에 값을 추가해도 ddl-auto: update가 DB의 허용값 목록을 넓혀주지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "festival_category", columnDefinition = "VARCHAR(20)")
    private FestivalCategory festivalCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "festival_status", columnDefinition = "VARCHAR(30)")
    private FestivalStatus festivalStatus;

    //취소 요청·승인이 동시에 들어와도 한쪽만 반영되도록 하는 낙관적 락 버전. 기존 행은 0으로 시작한다.
    @Version
    @Column(columnDefinition = "BIGINT DEFAULT 0")
    private Long version;

    //누적 조회수(IP당 24시간에 1회만 집계). 증가는 FestivalViewService가 bulk update로만 하므로
    //엔티티를 통해 바꾸지 않는다 — @Version 충돌 없이 동시 조회를 받기 위해서다. 기존 행은 0으로 시작한다.
    @Column(name = "view_count", columnDefinition = "BIGINT DEFAULT 0")
    @Builder.Default
    private Long viewCount = 0L;

    //주최자 귀책 취소 기록 — 요청자(주최자)와 승인자(운영자)를 분리해 전액 환불의 승인 근거를 남긴다.
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_approved_at")
    private Instant cancellationApprovedAt;

    @Column(name = "cancellation_approved_by_user_id")
    private Long cancellationApprovedByUserId;

    //취소 요청 직전 상태(PUBLISHED/CLOSED). 운영자가 반려하면 이 상태로 되돌린다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_cancellation", columnDefinition = "VARCHAR(30)")
    private FestivalStatus statusBeforeCancellation;

    //상태 검증은 FestivalCancellationService가 맡고, 엔티티는 전이만 기록한다.
    public void requestCancellation(Long actor, String reason) {
        statusBeforeCancellation = festivalStatus;
        festivalStatus = FestivalStatus.CANCELLATION_PENDING;
        cancelledByUserId = actor;
        cancelReason = reason;
    }

    //반려 — 요청 전 상태로 되돌리고 요청 기록을 지워 주최자가 다시 요청할 수 있게 한다.
    //이 컬럼이 생기기 전에 요청된 행은 이전 상태를 모르므로 종료 시각으로 판단한다.
    public void rejectCancellation() {
        if (statusBeforeCancellation != null) {
            festivalStatus = statusBeforeCancellation;
        } else {
            festivalStatus = endAt.isBefore(LocalDateTime.now()) ? FestivalStatus.CLOSED : FestivalStatus.PUBLISHED;
        }
        statusBeforeCancellation = null;
        cancelledByUserId = null;
        cancelReason = null;
    }

    public void approveCancellation(Long actor) {
        cancellationApprovedAt = Instant.now();
        cancellationApprovedByUserId = actor;
    }

    public void completeCancellation() {
        festivalStatus = FestivalStatus.CANCELLED;
        cancelledAt = Instant.now();
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted;

    //운영자 반려 사유 — 주최자가 보완해 다시 등록할 수 있도록 그대로 보여준다
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    //구역(SEATED 티켓타입) 배치 방식 — 페스티벌 전체에 하나만 적용된다. 기존 행은 전면형으로 채워진다.
    @Enumerated(EnumType.STRING)
    @Column(name = "stage_layout", columnDefinition = "VARCHAR(20) DEFAULT 'FRONT_STAGE'")
    @Builder.Default
    private FestivalStageLayout stageLayout = FestivalStageLayout.FRONT_STAGE;

    //운영자 심사: 공개 승인 처리 시작 — reservation-service 좌석 생성 확인 전까지는 비공개(PUBLISH_PENDING) 유지
    public void markPublishPending() {
        this.festivalStatus = FestivalStatus.PUBLISH_PENDING;
    }
    //운영자 심사: 공개 승인
    public void publish() {
        this.festivalStatus = FestivalStatus.PUBLISHED;
    }

    //운영자 심사: 반려(사유 필수)
    public void reject(String rejectReason) {
        this.festivalStatus = FestivalStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    //종료 배치: 개최 기간이 끝난 공개 페스티벌을 종료 처리한다
    public void close() {
        this.festivalStatus = FestivalStatus.CLOSED;
    }


}
