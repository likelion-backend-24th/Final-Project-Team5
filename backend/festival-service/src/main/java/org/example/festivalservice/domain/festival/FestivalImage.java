package org.example.festivalservice.domain.festival;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "festival_images")
public class FestivalImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "festival_id")
    private Festival festival;

    //방문자·주최자가 조회할 정적 이미지 경로 (예: /api/festivals/images/{filename})
    @Column(name = "image_url")
    private String imageUrl;

    //columnDefinition을 명시하지 않으면 Hibernate가 MySQL 네이티브 ENUM(...) 컬럼을 생성해,
    //Java enum에 값을 추가해도 ddl-auto: update가 DB의 허용값 목록을 넓혀주지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", columnDefinition = "VARCHAR(20)")
    private FestivalImageType imageType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
