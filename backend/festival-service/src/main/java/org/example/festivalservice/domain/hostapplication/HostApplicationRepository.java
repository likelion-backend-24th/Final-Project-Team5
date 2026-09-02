package org.example.festivalservice.domain.hostapplication;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostApplicationRepository extends JpaRepository<HostApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, HostApplicationStatus status);

    Optional<HostApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
