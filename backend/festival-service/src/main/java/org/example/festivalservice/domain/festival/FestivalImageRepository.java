package org.example.festivalservice.domain.festival;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalImageRepository extends JpaRepository<FestivalImage, Long> {

    //Festival 상세·목록 조회 시 소속 이미지를 조립할 때 사용
    List<FestivalImage> findByFestivalId(Long festivalId);
}
