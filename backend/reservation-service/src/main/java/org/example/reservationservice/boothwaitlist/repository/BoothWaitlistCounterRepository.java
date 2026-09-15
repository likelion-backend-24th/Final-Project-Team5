package org.example.reservationservice.boothwaitlist.repository;

import org.example.reservationservice.boothwaitlist.entity.BoothWaitlistCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoothWaitlistCounterRepository extends JpaRepository<BoothWaitlistCounter, Long> {

    //원자적 증가 — 동시에 여러 트랜잭션이 같은 부스에 대해 호출해도 MySQL 행 잠금이 순서대로 처리해준다.
    //clearAutomatically·flushAutomatically 없이는, 방금 save()로 영속화한 카운터가 영속성 컨텍스트(1차 캐시)에
    //남아 있어 이 bulk UPDATE 이후 findById()가 DB 최신값 대신 갱신 전 캐시된 값을 그대로 돌려준다(항상 0).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BoothWaitlistCounter c SET c.nextNumber = c.nextNumber + 1 WHERE c.boothId = :boothId")
    int increment(@Param("boothId") Long boothId);
}
