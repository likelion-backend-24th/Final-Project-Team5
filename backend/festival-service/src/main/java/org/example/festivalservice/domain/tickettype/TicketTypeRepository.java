package org.example.festivalservice.domain.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType,Long> {

    @Modifying
    @Query("UPDATE TicketType t SET t.remainQuantity = t.remainQuantity - :qty " +
            "WHERE t.id = :id AND t.remainQuantity >= :qty")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);
}
