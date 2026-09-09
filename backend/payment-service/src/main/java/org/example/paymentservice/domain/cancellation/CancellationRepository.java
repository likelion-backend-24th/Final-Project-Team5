package org.example.paymentservice.domain.cancellation;

import java.util.List;
import java.util.Optional;
import org.example.paymentservice.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CancellationRepository extends JpaRepository<Cancellation, Long> {

    //같은 논리 요청의 재시도인지 확인할 때(가이드 5.4 멱등 키)
    Optional<Cancellation> findByIdempotencyKey(String idempotencyKey);

    //PortOne 재조회 결과와 로컬을 대사할 때 cancellationId로 찾는다(가이드 9.4 UPSERT)
    Optional<Cancellation> findByCancellationId(String cancellationId);

    List<Cancellation> findByPayment(Payment payment);
}
