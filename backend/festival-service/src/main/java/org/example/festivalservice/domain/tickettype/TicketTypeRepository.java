package org.example.festivalservice.domain.tickettype;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType,Long> {

    //Festival 상세·목록 조회 시 소속 티켓종류를 조립할 때 사용
    java.util.List<TicketType> findByFestivalId(Long festivalId);

    @Modifying
    @Query("UPDATE TicketType t SET t.remainQuantity = t.remainQuantity - :qty " +
            "WHERE t.id = :id AND t.remainQuantity >= :qty")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    //결제 실패·취소 시 차감했던 재고를 원자적으로 복구한다. 총 수량을 넘지 않도록 조건으로 방지
    @Modifying
    @Query("UPDATE TicketType t SET t.remainQuantity = t.remainQuantity + :qty " +
            "WHERE t.id = :id AND t.remainQuantity + :qty <= t.totalQuantity")
    int restoreStock(@Param("id") Long id, @Param("qty") int qty);
}
