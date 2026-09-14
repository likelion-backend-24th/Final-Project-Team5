package org.example.festivalservice.domain.tickettype;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketTypeService {
    private final TicketTypeRepository ticketTypeRepository;
    private static final Logger log = LoggerFactory.getLogger(TicketTypeService.class);

    //재고 차감 메서드
    @Transactional
    public void deductStock(Long ticketTypeId, int quantity) {
        int updated = ticketTypeRepository.deductStock(ticketTypeId, quantity);
        if (updated == 0) {
            log.info("재고 차감 거부(재고 부족 또는 비공개 페스티벌): ticketType={}, qty={}", ticketTypeId, quantity);
            throw new ApiException(TicketTypeErrorCode.STOCK_EXCEEDED);
        }
        log.info("재고 차감: ticketType={}, qty={}", ticketTypeId, quantity);
    }

    //결제 실패·취소 시 차감했던 재고를 복구하는 메서드
    @Transactional
    public void restoreStock(Long ticketTypeId, int quantity) {
        int updated = ticketTypeRepository.restoreStock(ticketTypeId, quantity);
        if (updated == 0) {
            //총 수량을 넘기게 되는 복구는 조건절에 걸려 조용히 무시된다. 재고가 "사라지는" 원인을 나중에 추적할 수 있도록 남긴다.
            log.warn("재고 복구가 적용되지 않음(총 수량 초과 또는 없는 티켓): ticketType={}, qty={}", ticketTypeId, quantity);
            return;
        }
        log.info("재고 복구: ticketType={}, qty={}", ticketTypeId, quantity);
    }
}
