package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {
    List<Festival> findByFestivalStatusAndCancellationApprovedAtIsNotNull(FestivalStatus status);
    // 정산 후보 = 종료 24시간이 지난 공개·종료·취소 페스티벌
    @Query("""
            select f from Festival f
            where f.endAt <= :cutoff and f.festivalStatus in :statuses
            order by f.id
            """)
    List<Festival> findSettlementCandidates(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("statuses") Collection<FestivalStatus> statuses,
            Pageable pageable
    );
