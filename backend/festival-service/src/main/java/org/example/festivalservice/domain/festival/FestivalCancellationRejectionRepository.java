package org.example.festivalservice.domain.festival;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FestivalCancellationRejectionRepository extends JpaRepository<FestivalCancellationRejection, Long> {

    //운영자 취소 승인 화면 — 반려 이력 최신순
    List<FestivalCancellationRejection> findAllByOrderByRejectedAtDesc();
}