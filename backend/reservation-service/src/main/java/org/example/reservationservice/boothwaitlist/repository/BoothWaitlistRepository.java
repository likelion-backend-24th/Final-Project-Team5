package org.example.reservationservice.boothwaitlist.repository;

import java.util.Optional;
import org.example.reservationservice.boothwaitlist.entity.BoothWaitlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothWaitlistRepository extends JpaRepository<BoothWaitlist, Long> {

    boolean existsByBoothIdAndUserId(Long boothId, Long userId);

    Optional<BoothWaitlist> findByBoothIdAndUserId(Long boothId, Long userId);
}
