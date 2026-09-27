package org.example.paymentservice.domain.cancellation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.paymentservice.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    //같은 논리 요청의 재시도인지 확인할 때(가이드 5.4 멱등 키)
    Optional<Cancellation> findByIdempotencyKey(String idempotencyKey);

    //PortOne 재조회 결과와 로컬을 대사할 때 cancellationId로 찾는다(가이드 9.4 UPSERT)
    Optional<Cancellation> findByCancellationId(String cancellationId);

    List<Cancellation> findByPayment(Payment payment);

    //행사 취소 환불 미리보기 — 페스티벌들의 결제별 "이미 성공한 환불 액면가 합계"와 "액면가가 없는 취소 건수"
//결과: [결제 id, 액면가 합계, 액면가 없는 건수]
    @Query("""
        SELECT c.payment.id,
               SUM(c.grossAmount),
               SUM(CASE WHEN c.grossAmount IS NULL THEN 1 ELSE 0 END)
        FROM Cancellation c
        WHERE c.status = :status
          AND c.payment.festivalId IN :festivalIds
        GROUP BY c.payment.id
        """)
    List<Object[]> sumGrossByFestivalIds(@Param("festivalIds") Collection<Long> festivalIds,
                                         @Param("status") CancellationStatus status);
}
