package org.example.authservice.helper.repository;

import org.example.authservice.helper.entity.HelperInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface HelperInvitationRepository extends JpaRepository<HelperInvitation, Long> {
    Optional<HelperInvitation> findByTokenHash(String tokenHash);
    Optional<HelperInvitation> findByHelperUser_Id(Long helperUserId);
    boolean existsByFestivalIdAndNormalizedEmail(Long festivalId, String normalizedEmail);
    void deleteAllByHelperUser_Id(Long helperUserId);
}
