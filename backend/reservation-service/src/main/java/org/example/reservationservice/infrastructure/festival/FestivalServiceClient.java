package org.example.reservationservice.infrastructure.festival;

import lombok.RequiredArgsConstructor;
import org.example.reservationservice.infrastructure.festival.dto.FestivalApiEnvelope;
import org.example.reservationservice.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.example.reservationservice.infrastructure.festival.dto.StockAdjustRequestDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service 호출만 담당한다. 도메인 검증(PUBLISHED 여부, ticketType 소속 여부 등)은
 * 상위 서비스(ReservationService) 책임이다.
 *
 * 재고 차감·복구 API는 festival-service 쪽에 아직 내부 토큰 검증이 없어 인증 헤더 없이도
 * 호출되지만, 팀 내부 계약상 다른 서비스간 호출과 형평을 맞춰 여기서는 헤더를 붙이지 않는다.
 * festival-service에 토큰 검증이 추가되면 그때 같이 헤더를 붙여야 한다.
 */
@Component
@RequiredArgsConstructor
public class FestivalServiceClient {

    private final RestClient festivalServiceRestClient;

    public FestivalDetailResponseDto getFestival(Long festivalId) {
        FestivalApiEnvelope<FestivalDetailResponseDto> envelope = festivalServiceRestClient.get()
                .uri("/api/festivals/{id}", festivalId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return envelope == null ? null : envelope.data();
    }

    //예매 신청 시 재고를 원자적으로 차감한다. 재고 부족이면 festival-service가 409를 반환한다.
    public void deductStock(Long ticketTypeId, int quantity) {
        festivalServiceRestClient.patch()
                .uri("/internal/v1/ticket-types/{id}/stock", ticketTypeId)
                .body(new StockAdjustRequestDto(quantity))
                .retrieve()
                .toBodilessEntity();
    }

    //결제 실패·취소·만료 시 차감했던 재고를 복구한다.
    public void restoreStock(Long ticketTypeId, int quantity) {
        festivalServiceRestClient.patch()
                .uri("/internal/v1/ticket-types/{id}/stock/restore", ticketTypeId)
                .body(new StockAdjustRequestDto(quantity))
                .retrieve()
                .toBodilessEntity();
    }
}
