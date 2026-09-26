package org.example.paymentservice.domain.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    //정산 확정 직전에 결제 행을 잠그고 version을 대조한다 — 그 사이 환불이 끼어들면 확정을 거부하기 위해서다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(Long id);

    //정산·행사 환불 배치가 페스티벌의 예매 ID 목록으로 결제를 한 번에 조회할 때 사용
    List<Payment> findByReservationIdIn(Collection<Long> ids);

    Optional<Payment> findByPaymentId(String paymentId);

    // 데모 자동 입금 대상 — 가상계좌가 발급된 지 일정 시간이 지났는데 아직 입금 확인이 안 된 결제
    List<Payment> findByStatusAndVirtualAccountIssuedAtBefore(PaymentStatus status, Instant before);

    // 보상 환불 재시도 대상 — 예매 확정이 거절된 지 일정 시간이 지났는데 아직 전액 취소되지 않은 결제
    List<Payment> findByStatusInAndReservationRejectedAtBefore(Collection<PaymentStatus> statuses, Instant before);

    //행사 취소 환불 미리보기 — 환불 배치와 같은 대상(PAID·부분 취소, 예매 확정 거절 제외)의 결제를 가볍게 조회
//결과: [결제 id, 페스티벌 id, 결제 금액]
    @Query("""
        SELECT p.id, p.festivalId, p.ticketAmount FROM Payment p
        WHERE p.festivalId IN :festivalIds
          AND p.status IN :statuses
          AND p.reservationRejectedAt IS NULL
        """)
    List<Object[]> findRefundPreviewTargets(@Param("festivalIds") Collection<Long> festivalIds,
                                            @Param("statuses") Collection<PaymentStatus> statuses);
}
