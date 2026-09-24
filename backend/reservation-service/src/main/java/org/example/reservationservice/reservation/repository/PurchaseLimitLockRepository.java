package org.example.reservationservice.reservation.repository;

import jakarta.persistence.LockModeType;
import org.example.reservationservice.reservation.entity.PurchaseLimitLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseLimitLockRepository extends JpaRepository<PurchaseLimitLock, Long> {

    //잠금 행이 없으면 만들고 있으면 그대로 둔다. 동시에 두 요청이 처음 만들어도 유니크 제약 예외로 예매 트랜잭션이
    //롤백 표시되지 않고, 별도 트랜잭션(추가 DB 연결)도 쓰지 않는다 — 서비스당 연결 풀이 5개라 요청마다 연결을 2개씩
    //잡으면 동시 예매 때 풀이 바닥날 수 있다.
    @Modifying
    @Query(value = "INSERT INTO purchase_limit_locks (user_id, festival_id) VALUES (:userId, :festivalId) "
            + "ON DUPLICATE KEY UPDATE user_id = user_id", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("festivalId") Long festivalId);

    //같은 사용자·페스티벌의 한도 검사를 예매 트랜잭션이 끝날 때까지 한 번에 하나씩만 하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM PurchaseLimitLock l WHERE l.userId = :userId AND l.festivalId = :festivalId")
    Optional<PurchaseLimitLock> findForUpdate(@Param("userId") Long userId, @Param("festivalId") Long festivalId);
}
