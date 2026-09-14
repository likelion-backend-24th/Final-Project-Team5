package org.example.paymentservice.infrastructure.reservation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    //정산 계산에 쓰는 페스티벌의 전체 예매 스냅샷(수량·단가·환불 수량·결제 ID). 다른 서비스 DB를 직접 읽지 않기 위한 내부 API다.
    public List<ReservationForPaymentResponse> settlementContext(Long festivalId) {
        return Arrays.asList(Objects.requireNonNull(reservationServiceRestClient.get()
                .uri("/internal/v1/reservations/settlement-context?festivalId={id}", festivalId)
                .retrieve()
                .body(ReservationForPaymentResponse[].class)));
    }

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

    //주최자 귀책 전액 환불용 견적 — 위약금 0, 남은 수량 전부가 대상이다.
    public ReservationRefundQuoteResponse getOrganizerRefundQuote(Long id) {
        return reservationServiceRestClient.get()
                .uri("/internal/v1/reservations/{id}/organizer-refund-quote", id)
                .retrieve()
                .body(ReservationRefundQuoteResponse.class);
    }
}
