package org.example.festivalservice.domain.booth;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.festivalservice.domain.festival.Festival;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "booths")
public class Booth {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "festival_id")
    private Festival festival;

    //부스를 개설한 STOREHOST 계정 id. 지금은 권한 체크에 쓰지 않지만(등록만 역할 체크),
    //추후 "내 부스만 관리" 기능을 붙일 때를 대비해 미리 남겨둔다.
    @Column(name = "host_user_id")
    private Long hostUserId;

    private String title;

    private String description;

    @Column(name = "booth_host_name")
    private String boothHostName;

    //대표 이미지 1장(선택). 등록 전 /api/store/booths/images로 먼저 업로드해 URL을 받아 실어 보낸다.
    @Column(name = "image_url")
    private String imageUrl;

    //columnDefinition을 명시하지 않으면 Hibernate가 MySQL 네이티브 ENUM(...) 컬럼을 생성해,
    //Java enum에 값을 추가해도 ddl-auto: update가 DB의 허용값 목록을 넓혀주지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "booth_status", columnDefinition = "VARCHAR(20)")
    private BoothStatus boothStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void changeStatus(BoothStatus next) {
        this.boothStatus = next;
    }
}
