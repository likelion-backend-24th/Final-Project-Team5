package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalViewRepository extends JpaRepository<FestivalView, Long> {

    Optional<FestivalView> findByFestivalIdAndViewerHash(Long festivalId, String viewerHash);

    //조회수 증가 — 엔티티를 거치지 않는 bulk update라 Festival의 @Version을 건드리지 않고, 동시 조회끼리 충돌하지 않는다
    @Modifying
    @Query("update Festival f set f.viewCount = coalesce(f.viewCount, 0) + 1 where f.id = :festivalId")
    int incrementViewCount(@Param("festivalId") Long festivalId);

    //24시간이 지난 기록은 "다시 세도 되는" 상태와 같으므로 지워서 테이블을 작게 유지한다
    @Modifying
    @Query("delete from FestivalView v where v.viewedAt < :before")
    int deleteByViewedAtBefore(@Param("before") LocalDateTime before);
}
