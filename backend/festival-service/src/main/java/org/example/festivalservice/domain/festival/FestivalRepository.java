package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FestivalRepository extends JpaRepository<Festival, Long> {
    //운영자가 승인한 취소 요청 — payment-service 환불 배치가 이 목록을 대상으로 전액 환불을 진행한다
    List<Festival> findByFestivalStatusAndCancellationApprovedAtIsNotNull(FestivalStatus status);

    //정산 후보 = 종료 24시간이 지난 공개·종료·취소 페스티벌
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

    List<Festival> findByHostUserId(Long hostUserId);

    //방문자용 공개 목록 — 승인(공개) 상태만 페이징 조회
    Page<Festival> findByFestivalStatus(FestivalStatus festivalStatus, Pageable pageable);

    //방문자용 공개 목록 — 진행중(PUBLISHED)·종료(CLOSED) 상태를 함께 페이징 조회(종료된 것도 "종료됨" 배지로 계속 보여준다)
    Page<Festival> findByFestivalStatusIn(List<FestivalStatus> festivalStatuses, Pageable pageable);

    //운영자 심사 목록 — 상태별 전체 조회(페이징 없음)
    List<Festival> findByFestivalStatus(FestivalStatus festivalStatus);

    //운영자 심사 목록 — 처리된 것도 이력으로 보여주기 위해 상태와 무관하게 최신순 전체
    List<Festival> findAllByOrderByCreatedAtDesc();

    //방문자용 상세 — 승인(공개) 상태인 것만, 아니면 404 처리하도록 빈 Optional 반환
    Optional<Festival> findByIdAndFestivalStatus(Long id, FestivalStatus festivalStatus);

    //방문자용 상세 — 진행중·종료 상태를 함께 허용(그 외 상태는 존재 자체를 숨김)
    Optional<Festival> findByIdAndFestivalStatusIn(Long id, List<FestivalStatus> festivalStatuses);

    //종료 배치 — 기간이 끝났는데 아직 PUBLISHED인 페스티벌을 찾는다
    List<Festival> findByFestivalStatusAndEndAtBefore(FestivalStatus festivalStatus, LocalDateTime now);
}
