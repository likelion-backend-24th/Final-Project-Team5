package org.example.festivalservice.domain.festival;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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


    @Enumerated(EnumType.STRING)
    @Column(name = "festival_category")
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
}
