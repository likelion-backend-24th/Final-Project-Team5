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
    //개최 장소
    private String location;


    //columnDefinition을 명시하지 않으면 Hibernate가 MySQL 네이티브 ENUM(...) 컬럼을 생성해,
    //Java enum에 값을 추가해도 ddl-auto: update가 DB의 허용값 목록을 넓혀주지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "festival_category", columnDefinition = "VARCHAR(20)")
    private FestivalCategory festivalCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "festival_status")
    private FestivalStatus festivalStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted;

    //운영자 심사: 공개 승인
    public void publish() {
        this.festivalStatus = FestivalStatus.PUBLISHED;
    }

    //운영자 심사: 반려
    public void reject() {
        this.festivalStatus = FestivalStatus.REJECTED;
    }
}
