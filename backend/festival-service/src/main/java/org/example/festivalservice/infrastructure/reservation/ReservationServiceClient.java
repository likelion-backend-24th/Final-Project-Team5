package org.example.festivalservice.infrastructure.reservation;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.infrastructure.reservation.dto.SeatGenerationRequestDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Festival-Service → Reservation-Service 내부 호출 전담.
 * 어드민이 페스티벌을 승인(PUBLISHED)할 때, SEATED 티켓타입에 대해 실제 좌석을 생성해달라고 요청한다.
 * 도메인 검증(SEATED 여부 판단 등)은 상위 서비스(FestivalService) 책임이다.
 */
@Component
@RequiredArgsConstructor
public class ReservationServiceClient {

    private final RestClient reservationServiceRestClient;

    @Value("${internal.reservation-service.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //festivalId, ticketTypeId, zone, rows, seatsPerRow를 보내 실제 Seat 로우를 생성하게 한다.
    //reservation-service 쪽이 멱등 처리하므로 재시도해도 중복 생성되지 않는다.
    public void generateSeats(SeatGenerationRequestDto request) {
        reservationServiceRestClient.post()
                .uri("/internal/v1/seats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalAuthToken)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}