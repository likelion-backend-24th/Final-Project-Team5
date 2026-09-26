package org.example.festivalservice.domain.hostapplication;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostApplicationRepository extends JpaRepository<HostApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, HostApplicationStatus status);

    Optional<HostApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    List<HostApplication> findByStatus(HostApplicationStatus status);

    //운영자 심사 목록: 처리된 신청도 이력으로 보여주기 위해 상태와 무관하게 최신순 전체
    List<HostApplication> findAllByOrderByCreatedAtDesc();

    //본인의 신청 이력(반려→재신청 등) 최신순
    List<HostApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    //Role 부여 응답을 못 받아 APPROVAL_PENDING에 머문 신청 — 재시도 배치용
    List<HostApplication> findByStatusAndUpdatedAtBefore(HostApplicationStatus status, LocalDateTime before);

    //어드민 대시보드 — 상태 묶음별 신청 건수
    long countByStatusIn(Collection<HostApplicationStatus> statuses);
}
