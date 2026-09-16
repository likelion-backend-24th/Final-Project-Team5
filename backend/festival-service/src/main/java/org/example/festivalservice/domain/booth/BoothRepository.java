package org.example.festivalservice.domain.booth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    //관람자용 공개 목록 — WAITING은 숨기고 OPEN·CLOSED만 노출
    List<Booth> findByFestivalIdAndBoothStatusIn(Long festivalId, List<BoothStatus> visibleStatuses);

    //관람자용 상세 — WAITING이면 존재 자체를 숨긴다(404 처리하도록 빈 Optional 반환)
    Optional<Booth> findByIdAndBoothStatusIn(Long id, List<BoothStatus> visibleStatuses);

    //STOREHOST 본인이 개설한 부스 목록(상태 무관, 본인 화면이라 WAITING도 보여야 한다)
    List<Booth> findByHostUserId(Long hostUserId);

    //페스티벌당 부스 1개 제한 체크용
    boolean existsByFestivalId(Long festivalId);
}
