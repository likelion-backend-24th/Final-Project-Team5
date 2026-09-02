package org.example.festivalservice.domain.festival;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    List<Festival> findByHostUserId(Long hostUserId);

    //방문자용 공개 목록 — 승인(공개) 상태만 페이징 조회
    Page<Festival> findByFestivalStatus(FestivalStatus festivalStatus, Pageable pageable);

    //방문자용 상세 — 승인(공개) 상태인 것만, 아니면 404 처리하도록 빈 Optional 반환
    Optional<Festival> findByIdAndFestivalStatus(Long id, FestivalStatus festivalStatus);
}
