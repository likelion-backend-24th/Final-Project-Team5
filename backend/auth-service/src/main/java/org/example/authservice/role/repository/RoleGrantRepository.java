package org.example.authservice.role.repository;

import java.util.Optional;
import org.example.authservice.role.entity.RoleGrant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleGrantRepository extends JpaRepository<RoleGrant, Long> {

    Optional<RoleGrant> findByApplicationId(Long applicationId);
}
