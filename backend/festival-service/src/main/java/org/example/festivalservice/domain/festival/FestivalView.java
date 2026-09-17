package org.example.festivalservice.domain.festival;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "이 IP가 이 페스티벌을 마지막으로 언제 봤는지" — 페스티벌·IP 쌍당 한 행만 두고 viewedAt을 갱신한다.
 * IP 원문은 저장하지 않고 SHA-256 해시만 둔다(집계용이라 원문이 필요 없다). 24시간이 지난 행은 배치가 지운다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "festival_views",
        uniqueConstraints = @UniqueConstraint(name = "uk_festival_views_festival_viewer", columnNames = {"festival_id", "viewer_hash"}))
public class FestivalView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    @Column(name = "viewer_hash", nullable = false, length = 64)
    private String viewerHash;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public FestivalView(Long festivalId, String viewerHash, LocalDateTime viewedAt) {
        this.festivalId = festivalId;
        this.viewerHash = viewerHash;
        this.viewedAt = viewedAt;
    }

    public void touch(LocalDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }
}
