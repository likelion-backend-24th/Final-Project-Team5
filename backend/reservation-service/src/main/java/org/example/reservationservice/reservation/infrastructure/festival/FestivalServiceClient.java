package org.example.reservationservice.reservation.infrastructure.festival;

import lombok.RequiredArgsConstructor;
import org.example.reservationservice.reservation.infrastructure.festival.dto.BoothDetailResponseDto;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalApiEnvelope;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.example.reservationservice.reservation.infrastructure.festival.dto.StockAdjustRequestDto;
import org.example.reservationservice.reservation.infrastructure.festival.dto.StoreBoothOwnerResponseDto;
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

    //부스 대기 신청 시 boothId → festivalId·상태를 확인하기 위해 호출한다(공개 조회 API라 헤더 불필요).
    //WAITING 상태 부스는 festival-service가 애초에 404로 숨겨준다.
    public BoothDetailResponseDto getBooth(Long boothId) {
        FestivalApiEnvelope<BoothDetailResponseDto> envelope = festivalServiceRestClient.get()
                .uri("/api/booths/{id}", boothId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return envelope == null ? null : envelope.data();
    }

    //대기 호출(call-next) 소유권 검증용 — STOREHOST 본인 전용 API를 그대로 호출하되, 요청자의
    //X-User-Id·X-User-Role을 그대로 전달해 "본인인지"를 festival-service가 판정하게 한다
    //(별도 내부 토큰 계약을 새로 만들지 않고 기존 STOREHOST 전용 API를 재사용).
    public StoreBoothOwnerResponseDto getMyBooth(Long boothId, Long userId, String role) {
        FestivalApiEnvelope<StoreBoothOwnerResponseDto> envelope = festivalServiceRestClient.get()
                .uri("/api/store/booths/{id}", boothId)
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Role", role)
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
