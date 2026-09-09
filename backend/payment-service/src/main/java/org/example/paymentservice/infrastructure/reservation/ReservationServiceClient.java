package org.example.paymentservice.infrastructure.reservation;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.infrastructure.reservation.dto.CancelReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ExtendReservationHoldRequest;
import org.example.paymentservice.infrastructure.reservation.dto.RefundReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationRefundQuoteResponse;
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

    public void confirmReservation(Long reservationId, ConfirmReservationRequest request) {
        reservationServiceRestClient.patch()
                .uri("/internal/v1/reservations/{id}/confirm", reservationId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void cancelReservation(Long reservationId, CancelReservationRequest request) {
        reservationServiceRestClient.patch()
                .uri("/internal/v1/reservations/{id}/cancel", reservationId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void extendReservationHold(Long reservationId, ExtendReservationHoldRequest request) {
        reservationServiceRestClient.patch()
                .uri("/internal/v1/reservations/{id}/extend-hold", reservationId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    //환불 금액은 공연 일정을 아는 Reservation-Service가 계산한다. 여기서는 그 견적을 받아올 뿐이다.
    public ReservationRefundQuoteResponse getRefundQuote(Long reservationId, Integer quantity) {
        return reservationServiceRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/v1/reservations/{id}/refund-quote")
                        .queryParamIfPresent("quantity", java.util.Optional.ofNullable(quantity))
                        .build(reservationId))
                .retrieve()
                .body(ReservationRefundQuoteResponse.class);
    }

    //PortOne 취소가 성공한 뒤 예매 상태 확정 + 재고 복구를 요청한다.
    public void refundReservation(Long reservationId, RefundReservationRequest request) {
        reservationServiceRestClient.patch()
                .uri("/internal/v1/reservations/{id}/refund", reservationId)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
