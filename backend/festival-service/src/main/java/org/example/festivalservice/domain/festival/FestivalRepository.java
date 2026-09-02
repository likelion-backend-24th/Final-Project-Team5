package org.example.festivalservice.domain.festival;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRepository extends JpaRepository<Festival, Long> {

    List<Festival> findByHostUserId(Long hostUserId);
}
