package org.example.festivalservice.domain.festival;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FestivalImageRepository extends JpaRepository<FestivalImage, Long> {

    //Festival 상세·목록 조회 시 소속 이미지를 조립할 때 사용
    List<FestivalImage> findByFestivalId(Long festivalId);

    //어드민 목록 — 한 페이지의 페스티벌들에 속한 이미지를 한 번에 조회(N+1 방지)
    List<FestivalImage> findByFestivalIdIn(Collection<Long> festivalIds);
}
