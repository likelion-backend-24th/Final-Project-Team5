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

    //AI 추천 후보 — 공개 중이고 아직 끝나지 않은 페스티벌
    List<Festival> findByFestivalStatusAndEndAtAfter(FestivalStatus festivalStatus, LocalDateTime now);

    //좌석 생성 요청(reservation-service 호출)이 실패해 PUBLISH_PENDING에 머문 페스티벌 — 재시도 배치용
    //(HostApplicationRepository.findByStatusAndUpdatedAtBefore와 같은 패턴)
    List<Festival> findByFestivalStatusAndUpdatedAtBefore(FestivalStatus festivalStatus, LocalDateTime before);

    //좌표 백필 대상 — 카카오맵 기능 이전에 등록됐거나 지도를 안 쓰고 등록해 좌표가 없는 페스티벌
    List<Festival> findByLatitudeIsNull();

    //운영자 심사 목록 — 상태 묶음 + 검색(페스티벌명 또는 주최자) + 페이징. 정렬은 Pageable로 받는다
    @Query("""
        SELECT f FROM Festival f
        WHERE f.festivalStatus IN :statuses
          AND (:keyword IS NULL
               OR f.name LIKE CONCAT('%', :keyword, '%')
               OR f.hostUserId IN :hostIds)
        """)
    Page<Festival> searchForAdmin(@Param("keyword") String keyword,
                                  @Param("statuses") Collection<FestivalStatus> statuses,
                                  @Param("hostIds") Collection<Long> hostIds,
                                  Pageable pageable);

    //운영자 주최자 목록 — 주최자별 등록 페스티벌 개수(상태 무관)
    @Query("""
        SELECT f.hostUserId, COUNT(f) FROM Festival f
        WHERE f.hostUserId IN :hostIds
        GROUP BY f.hostUserId
        """)
    List<Object[]> countByHostUserIds(@Param("hostIds") Collection<Long> hostIds);

    //어드민 운영 현황 — 공개된 적 있는 페스티벌을 운영 상태(예정/진행 중/종료/취소)·검색으로 조회
//운영 상태는 festivalStatus와 "지금" 시각을 함께 보고 판단한다(종료 배치가 아직 안 돈 PUBLISHED도 종료로 본다)
    @Query("""
        SELECT f FROM Festival f
        WHERE f.festivalStatus IN :visibleStatuses
          AND (:keyword IS NULL
               OR f.name LIKE CONCAT('%', :keyword, '%')
               OR f.hostUserId IN :hostIds)
          AND (:operationStatus = 'ALL'
               OR (:operationStatus = 'SCHEDULED'
                   AND f.festivalStatus = org.example.festivalservice.domain.festival.FestivalStatus.PUBLISHED
                   AND f.startAt > :now)
               OR (:operationStatus = 'ONGOING'
                   AND f.festivalStatus = org.example.festivalservice.domain.festival.FestivalStatus.PUBLISHED
                   AND f.startAt <= :now AND f.endAt >= :now)
               OR (:operationStatus = 'CLOSED'
                   AND (f.festivalStatus = org.example.festivalservice.domain.festival.FestivalStatus.CLOSED
                        OR (f.festivalStatus = org.example.festivalservice.domain.festival.FestivalStatus.PUBLISHED
                            AND f.endAt < :now)))
               OR (:operationStatus = 'CANCELLED'
                   AND f.festivalStatus IN (
                       org.example.festivalservice.domain.festival.FestivalStatus.CANCELLATION_PENDING,
                       org.example.festivalservice.domain.festival.FestivalStatus.CANCELLED)))
        """)
    Page<Festival> searchOperationsForAdmin(@Param("keyword") String keyword,
                                            @Param("hostIds") Collection<Long> hostIds,
                                            @Param("visibleStatuses") Collection<FestivalStatus> visibleStatuses,
                                            @Param("operationStatus") String operationStatus,
                                            @Param("now") LocalDateTime now,
                                            Pageable pageable);
}
