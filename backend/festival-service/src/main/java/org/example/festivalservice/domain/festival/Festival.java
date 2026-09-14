package org.example.festivalservice.domain.festival;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @Column(name = "festival_status", columnDefinition = "VARCHAR(20)")
    private FestivalStatus festivalStatus;

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
