package org.example.festivalservice.domain.tickettype;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketTypeService {
    private final TicketTypeRepository ticketTypeRepository;

    //재고 차감 메서드
    @Transactional
    public void deductStock(Long ticketTypeId, int quantity) {
        int updated = ticketTypeRepository.deductStock(ticketTypeId, quantity);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STOCK_EXCEEDED", "재고가 부족합니다");
        }
    }

    //결제 실패·취소 시 차감했던 재고를 복구하는 메서드
    @Transactional
    public void restoreStock(Long ticketTypeId, int quantity) {
        ticketTypeRepository.restoreStock(ticketTypeId, quantity);
    }
}
