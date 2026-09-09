package org.example.paymentservice.domain.cancellation;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.dto.PaymentCancellationResponse;
import org.example.paymentservice.domain.payment.Payment;
import org.example.paymentservice.domain.payment.PaymentErrorCode;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.RefundReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationRefundQuoteResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 전체·부분 취소(환불) 처리. PortOne V2 결제 연동 실전 가이드 9장을 그대로 따른다.
 *
 * 핵심 원칙 두 가지:
 * 1. 우리 요청이든 외부(PortOne 대시보드) 취소든, 최종 상태는 항상 PortOne 재조회 결과로 확정한다.
 * 2. 환불 금액은 우리가 정하지 않는다 — 공연 일정을 아는 Reservation-Service의 견적을 받아 집행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancellationService {

    private final PaymentRepository paymentRepository;
    private final CancellationRepository cancellationRepository;
    private final PortOnePaymentClient portOnePaymentClient;
    private final ReservationServiceClient reservationServiceClient;

    /**
     * 참가자가 환불을 요청한다.
     *
     * @param quantity 환불할 장수. null이면 남은 전량 환불(가이드 9.3의 "전체 취소").
     */
    public PaymentCancellationResponse cancel(Long userId, String paymentId, Integer quantity,
                                              String reason, String idempotencyKey) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getUserId().equals(userId)) {
            throw new ApiException(PaymentErrorCode.FORBIDDEN_PAYMENT_OWNER);
        }

        //같은 논리 요청의 재시도는 새로 취소하지 않고 이미 만든 취소 결과를 그대로 돌려준다(가이드 5.4).
        Optional<Cancellation> alreadyRequested = cancellationRepository.findByIdempotencyKey(idempotencyKey);
        if (alreadyRequested.isPresent()) {
            return toResponse(payment, alreadyRequested.get());
        }

        if (!payment.getStatus().canTransitionTo(PaymentStatus.PARTIAL_CANCELLED)
                && !payment.getStatus().canTransitionTo(PaymentStatus.CANCELLED)) {
            throw new ApiException(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE);
        }

        ReservationRefundQuoteResponse quote = getRefundQuoteOrThrow(payment.getReservationId(), quantity);
        if (!quote.refundable()) {
            throw new ApiException(toRefundErrorCode(quote.rejectReason()));
        }

        //먼저 REQUESTED로 남긴다 — PortOne 호출 직후 서버가 죽어도 "요청한 적 있음"이 남아야 대사가 가능하다(가이드 9.1).
        Cancellation cancellation = cancellationRepository.save(Cancellation.builder()
                .payment(payment)
                .idempotencyKey(idempotencyKey)
                .status(CancellationStatus.REQUESTED)
                .source(CancellationSource.API_REQUEST)
                .amount(quote.refundAmount())
                .quantity(quote.refundQuantity())
                .reason(reason)
                .build());

        try {
            PortOneCancelResponse cancelled =
                    portOnePaymentClient.cancelPayment(paymentId, quote.refundAmount(), reason, idempotencyKey);
            if (cancelled != null && cancelled.cancellation() != null) {
                cancellation.syncFrom(cancelled.cancellation().id(),
                        toCancellationStatus(cancelled.cancellation().status()),
                        cancelled.cancellation().cancelledAt());
                cancellationRepository.save(cancellation);
            }
        } catch (RestClientException e) {
            cancellation.markFailed();
            cancellationRepository.save(cancellation);
            log.warn("PortOne 취소 요청 실패. paymentId={}, amount={}", paymentId, quote.refundAmount(), e);
            throw new ApiException(PaymentErrorCode.REFUND_FAILED);
        }

        //응답 하나만 믿지 않고 재조회로 전체 취소 목록을 맞춘다(가이드 9.1).
        reconcile(payment);

        //PortOne 취소가 확정된 뒤에야 예매·재고를 되돌린다. 순서가 반대면 취소 실패 시 재고만 풀린다.
        reservationServiceClient.refundReservation(payment.getReservationId(),
                new RefundReservationRequest(paymentId, quote.refundQuantity()));

        return toResponse(payment, cancellation);
    }

    /**
     * PortOne 재조회 결과로 로컬 취소 이력과 결제 상태를 맞춘다.
     * 취소 웹훅(외부 취소 포함)도 이 메서드를 그대로 재사용한다 — 가이드 9.4.
     */
    public void reconcile(Payment payment) {
        PortOnePaymentResponse remote = portOnePaymentClient.getPayment(payment.getPaymentId());
        if (remote == null) {
            return;
        }

        upsertCancellations(payment, remote.cancellations());
        applyCancelledAmount(payment, remote);
    }

    public void reconcileByPaymentId(String paymentId) {
        paymentRepository.findByPaymentId(paymentId).ifPresent(this::reconcile);
    }

    //PortOne이 알려준 취소 목록을 로컬에 UPSERT한다. 로컬에 없는 건은 외부에서 발생한 취소다.
    private void upsertCancellations(Payment payment, List<PortOnePaymentResponse.Cancellation> remoteCancellations) {
        if (remoteCancellations == null) {
            return;
        }

        for (PortOnePaymentResponse.Cancellation remote : remoteCancellations) {
            CancellationStatus remoteStatus = toCancellationStatus(remote.status());

            Optional<Cancellation> local = Optional.ofNullable(remote.id())
                    .flatMap(cancellationRepository::findByCancellationId);
            if (local.isPresent()) {
                Cancellation cancellation = local.get();
                cancellation.syncFrom(remote.id(), remoteStatus, remote.cancelledAt());
                cancellationRepository.save(cancellation);
                continue;
            }

            //우리가 요청해 두고 아직 cancellationId를 못 받은 건이 있으면 그 행에 붙인다.
            Optional<Cancellation> pendingOurs = cancellationRepository.findByPayment(payment).stream()
                    .filter(c -> c.getCancellationId() == null)
                    .findFirst();
            if (pendingOurs.isPresent()) {
                Cancellation cancellation = pendingOurs.get();
                cancellation.syncFrom(remote.id(), remoteStatus, remote.cancelledAt());
                cancellationRepository.save(cancellation);
                continue;
            }

            //그래도 없으면 PortOne 대시보드 등 외부에서 취소된 건이다(가이드 9.4).
            //장수를 알 수 없으므로 0으로 두고, 예매 쪽 반영은 사람이 확인해야 한다.
            log.warn("외부에서 발생한 취소를 발견했다. paymentId={}, cancellationId={}, amount={}",
                    payment.getPaymentId(), remote.id(), remote.totalAmount());
            cancellationRepository.save(Cancellation.builder()
                    .payment(payment)
                    .cancellationId(remote.id())
                    .status(remoteStatus)
                    .source(CancellationSource.WEBHOOK_DISCOVERED)
                    .amount(remote.totalAmount())
                    .quantity(0)
                    .reason(remote.reason())
                    .cancelledAt(remote.cancelledAt())
                    .build());
        }
    }

    //누적 취소액은 개별 취소 행을 더하지 않고 PortOne이 계산해준 amount.cancelled를 신뢰한다(가이드 9.1 "성공 취소액 재계산").
    private void applyCancelledAmount(Payment payment, PortOnePaymentResponse remote) {
        if (remote.amount() == null) {
            return;
        }
        long cancelledTotal = remote.amount().cancelled();
        if (cancelledTotal <= 0) {
            return;
        }

        PaymentStatus next = cancelledTotal >= payment.totalAmount()
                ? PaymentStatus.CANCELLED
                : PaymentStatus.PARTIAL_CANCELLED;
        if (payment.getStatus() == next && next == PaymentStatus.CANCELLED) {
            return;
        }
        if (!payment.getStatus().canTransitionTo(next)) {
            //이미 CANCELLED인데 늦게 도착한 부분취소 이벤트가 상태를 되돌리는 걸 막는다(가이드 9.5).
            return;
        }
        payment.transitionTo(next);
        paymentRepository.save(payment);
    }

    private ReservationRefundQuoteResponse getRefundQuoteOrThrow(Long reservationId, Integer quantity) {
        try {
            return reservationServiceClient.getRefundQuote(reservationId, quantity);
        } catch (RestClientException e) {
            throw new ApiException(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE);
        }
    }

    //Reservation-Service의 거절 사유를 그대로 대응되는 결제 에러코드로 옮긴다.
    //화면이 "환불 창 마감"과 "이미 입장"을 구분해 안내해야 해서 하나로 뭉뚱그리지 않는다.
    private static PaymentErrorCode toRefundErrorCode(String rejectReason) {
        if (rejectReason == null) {
            return PaymentErrorCode.REFUND_NOT_ALLOWED;
        }
        return switch (rejectReason) {
            case "REFUND_WINDOW_CLOSED" -> PaymentErrorCode.REFUND_WINDOW_CLOSED;
            case "ALREADY_CHECKED_IN_NOT_REFUNDABLE" -> PaymentErrorCode.ALREADY_CHECKED_IN_NOT_REFUNDABLE;
            case "REFUND_QUANTITY_EXCEEDED" -> PaymentErrorCode.REFUND_QUANTITY_EXCEEDED;
            default -> PaymentErrorCode.REFUND_NOT_ALLOWED;
        };
    }

    private static CancellationStatus toCancellationStatus(String remoteStatus) {
        if (remoteStatus == null) {
            return CancellationStatus.PENDING;
        }
        try {
            return CancellationStatus.valueOf(remoteStatus);
        } catch (IllegalArgumentException e) {
            //모르는 상태 문자열이 오면 확정 상태로 못 박지 않고 중간 상태로 둔다.
            log.warn("알 수 없는 PortOne 취소 상태: {}", remoteStatus);
            return CancellationStatus.PENDING;
        }
    }

    private PaymentCancellationResponse toResponse(Payment payment, Cancellation cancellation) {
        return new PaymentCancellationResponse(
                payment.getPaymentId(),
                cancellation.getCancellationId(),
                cancellation.getStatus().name(),
                cancellation.getAmount(),
                cancellation.getQuantity(),
                payment.getStatus().name()
        );
    }
}
