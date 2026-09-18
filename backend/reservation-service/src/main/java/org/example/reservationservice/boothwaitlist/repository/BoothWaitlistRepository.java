package org.example.reservationservice.boothwaitlist.repository;

import java.util.List;
import java.util.Optional;
import org.example.reservationservice.boothwaitlist.entity.BoothWaitlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothWaitlistRepository extends JpaRepository<BoothWaitlist, Long> {

    boolean existsByBoothIdAndUserId(Long boothId, Long userId);

    Optional<BoothWaitlist> findByBoothIdAndUserId(Long boothId, Long userId);

    //챗봇 위젯이 폴링으로 "내 차례가 됐는지" 확인할 때 쓴다 — 여러 부스에 걸쳐 신청했을 수 있다.
    List<BoothWaitlist> findByUserId(Long userId);
}
