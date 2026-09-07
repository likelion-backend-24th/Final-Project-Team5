package org.example.paymentservice.infrastructure.reservation;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reservation-Service 내부 API 호출만 담당한다. 소유권·상태 검증은 상위 서비스(PaymentService) 책임이다. */
@Component
@RequiredArgsConstructor
public class ReservationServiceClient {

    private final RestClient reservationServiceRestClient;

    public ReservationForPaymentResponse getReservation(Long reservationId) {
        return reservationServiceRestClient.get()
                .uri("/internal/v1/reservations/{id}", reservationId)
                .retrieve()
                .body(ReservationForPaymentResponse.class);
    }
}
