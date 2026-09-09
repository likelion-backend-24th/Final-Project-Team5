package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    List<Festival> findByHostUserId(Long hostUserId);

    //방문자용 공개 목록 — 승인(공개) 상태만 페이징 조회
    Page<Festival> findByFestivalStatus(FestivalStatus festivalStatus, Pageable pageable);

    //방문자용 공개 목록 — 진행중(PUBLISHED)·종료(CLOSED) 상태를 함께 페이징 조회(종료된 것도 "종료됨" 배지로 계속 보여준다)
    Page<Festival> findByFestivalStatusIn(List<FestivalStatus> festivalStatuses, Pageable pageable);

    //운영자 심사 목록 — 상태별 전체 조회(페이징 없음)
    List<Festival> findByFestivalStatus(FestivalStatus festivalStatus);

    //방문자용 상세 — 승인(공개) 상태인 것만, 아니면 404 처리하도록 빈 Optional 반환
    Optional<Festival> findByIdAndFestivalStatus(Long id, FestivalStatus festivalStatus);

    //방문자용 상세 — 진행중·종료 상태를 함께 허용(그 외 상태는 존재 자체를 숨김)
    Optional<Festival> findByIdAndFestivalStatusIn(Long id, List<FestivalStatus> festivalStatuses);

    //종료 배치 — 기간이 끝났는데 아직 PUBLISHED인 페스티벌을 찾는다
    List<Festival> findByFestivalStatusAndEndAtBefore(FestivalStatus festivalStatus, LocalDateTime now);
}
