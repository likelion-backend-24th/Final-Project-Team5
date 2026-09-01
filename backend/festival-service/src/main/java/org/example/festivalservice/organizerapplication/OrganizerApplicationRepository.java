package org.example.festivalservice.organizerapplication;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerApplicationRepository extends JpaRepository<OrganizerApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, OrganizerApplicationStatus status);

    Optional<OrganizerApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
