package org.example.paymentservice.domain.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.id = :id")
    Optional<Payment> findLockedById(Long id);
    java.util.List<Payment> findByReservationIdIn(java.util.Collection<Long> ids);
    Optional<Payment> findByPaymentId(String paymentId);
}
