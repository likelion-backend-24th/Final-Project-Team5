package org.example.paymentservice.domain.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.dto.PaymentCompleteResponse;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.CancelReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ConfirmReservationRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ExtendReservationHoldRequest;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String RESERVATION_PAYABLE_STATUS = "PENDING";
    private static final String PORTONE_STATUS_PAID = "PAID";
    private static final String PORTONE_STATUS_FAILED = "FAILED";
    private static final String PORTONE_STATUS_VIRTUAL_ACCOUNT_ISSUED = "VIRTUAL_ACCOUNT_ISSUED";
    private static final String PORTONE_CHANNEL_TYPE_TEST = "TEST";
    private static final String CANCEL_REASON_PAYMENT_FAILED = "PAYMENT_FAILED";

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ReservationServiceClient reservationServiceClient;
    private final PortOnePaymentClient portOnePaymentClient;

    @Value("${payment.id-prefix}")
    private String paymentIdPrefix;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key.payment}")
    private String channelKeyPayment;

    // 참가자가 Pending 예매의 결제를 시작할 때: 예매 소유권·상태를 확인하고 Payment를 READY로 생성한다.
    // Reservation-Service 조회(외부 호출)를 DB 트랜잭션 안에 넣지 않는다(실전 가이드 11.4·HostApplicationService.review 참고).
    // 단일 save() 호출은 Spring Data JPA가 자체적으로 트랜잭션을 감싸주므로 별도 선언이 필요 없다.
    public PaymentPrepareResponse prepare(Long userId, PaymentPrepareRequest request) {
        ReservationForPaymentResponse reservation = getReservationOrThrow(request.reservationId());

        if (!reservation.userId().equals(userId)) {
            throw new ApiException(PaymentErrorCode.FORBIDDEN_RESERVATION_OWNER);
        }
        if (!RESERVATION_PAYABLE_STATUS.equals(reservation.status())) {
            throw new ApiException(PaymentErrorCode.RESERVATION_NOT_PAYABLE);
        }

        Payment payment = Payment.builder()
                .paymentId(paymentIdPrefix + UUID.randomUUID())
                .reservationId(reservation.reservationId())
                .userId(userId)
                .ticketAmount(reservation.totalAmount())
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.READY)
                .build();
        paymentRepository.save(payment);

        return new PaymentPrepareResponse(payment.getPaymentId(), storeId, channelKeyPayment, payment.totalAmount());
    }

    // 브라우저 완료 요청: 본인 결제인지 확인한 뒤 동기화 로직(syncPayment)에 위임한다.
    public PaymentCompleteResponse complete(Long userId, String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getUserId().equals(userId)) {
            throw new ApiException(PaymentErrorCode.FORBIDDEN_PAYMENT_OWNER);
        }
        return syncPayment(payment);
    }

    // PortOne 조회로 재검증한 뒤 예매를 확정하거나 취소한다. 완료 API와 웹훅(Task 7-5)이 이 메서드를
    // 그대로 재사용해 같은 동기화 로직을 공유한다(실전 가이드 7.3) — 웹훅은 로그인 사용자가 없으므로
    // 소유권 검증 없이 바로 이 메서드를 호출한다(인증은 PortOne 서명이 대신한다, 실전 가이드 12.2).
    // PortOne 조회와 Reservation-Service 호출(둘 다 외부 네트워크 호출) 동안 DB 트랜잭션을 열어두지 않는다
    // (실전 가이드 11.4 "PortOne 조회는 DB 트랜잭션 밖에서 수행"). 각 상태 반영은 개별 save() 호출로 짧게 끝낸다.
    public PaymentCompleteResponse syncPayment(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return syncPayment(payment);
    }

    private PaymentCompleteResponse syncPayment(Payment payment) {
        if (isFinalized(payment.getStatus())) {
            // 완료 API가 반복 호출돼도 오류 대신 현재 성공 상태를 그대로 반환한다(멱등).
            return toCompleteResponse(payment);
        }

        PortOnePaymentResponse remote;
        try {
            remote = portOnePaymentClient.getPayment(payment.getPaymentId());
        } catch (HttpClientErrorException.NotFound e) {
            // 브라우저가 PortOne 결제창을 실제로 완료하기 전에 completed API가 먼저 호출된 경우
            // (더블클릭, 뒤로가기 등) PortOne이 이 paymentId를 아직 모른다 — 500 대신 409로 알려준다.
            throw new ApiException(PaymentErrorCode.PAYMENT_NOT_YET_PROCESSED);
        }
        validateRemotePayment(payment, remote);
        recordTransaction(payment, remote);

        switch (remote.status()) {
            case PORTONE_STATUS_PAID -> handlePaid(payment, remote);
            case PORTONE_STATUS_FAILED -> handleFailed(payment, remote);
            case PORTONE_STATUS_VIRTUAL_ACCOUNT_ISSUED -> handleVirtualAccountIssued(payment, remote);
            default -> throw new ApiException(PaymentErrorCode.UNEXPECTED_PAYMENT_STATUS);
        }

        return toCompleteResponse(payment);
    }

    private void handlePaid(Payment payment, PortOnePaymentResponse remote) {
        payment.transitionTo(PaymentStatus.PAID);
        paymentRepository.save(payment);

        String payMethod = remote.method() != null ? remote.method().type() : null;
        ConfirmReservationRequest request = new ConfirmReservationRequest(
                payment.getPaymentId(), remote.amount().total(), payMethod, remote.paidAt());
        try {
            reservationServiceClient.confirmReservation(payment.getReservationId(), request);
        } catch (HttpClientErrorException.Conflict e) {
            // 결제는 확정됐지만 예매가 배치로 이미 만료·취소된 엣지 케이스. 자동 환불(PortOne 취소 API)은
            // Story9 범위라 아직 구현 전이므로, 지금은 예외로 드러내고 정합성 배치(추후 구현)가 발견하도록 남긴다.
            log.warn("결제는 확정됐지만 예매 확정 실패(이미 종료된 예매로 추정). paymentId={}, reservationId={}",
                    payment.getPaymentId(), payment.getReservationId(), e);
            throw new ApiException(PaymentErrorCode.RESERVATION_ALREADY_FINALIZED);
        }
    }

    private void handleFailed(Payment payment, PortOnePaymentResponse remote) {
        payment.transitionTo(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        CancelReservationRequest request = new CancelReservationRequest(payment.getPaymentId(), CANCEL_REASON_PAYMENT_FAILED);
        reservationServiceClient.cancelReservation(payment.getReservationId(), request);
    }

    private void handleVirtualAccountIssued(Payment payment, PortOnePaymentResponse remote) {
        payment.transitionTo(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);
        paymentRepository.save(payment);

        if (remote.method() != null && remote.method().expiredAt() != null) {
            reservationServiceClient.extendReservationHold(
                    payment.getReservationId(), new ExtendReservationHoldRequest(remote.method().expiredAt()));
        }
    }

    // 프론트 결과나 웹훅 본문의 금액·통화를 신뢰하지 않고, PortOne 조회 결과를 우리 주문금액과 대조한다.
    private void validateRemotePayment(Payment payment, PortOnePaymentResponse remote) {
        boolean storeMatches = storeId.equals(remote.storeId());
        boolean channelMatches = remote.channel() != null && channelKeyPayment.equals(remote.channel().key());
        boolean isTestChannel = remote.channel() != null && PORTONE_CHANNEL_TYPE_TEST.equals(remote.channel().type());
        boolean currencyMatches = payment.getCurrency().equals(remote.currency());
        boolean amountMatches = remote.amount() != null && payment.totalAmount() == remote.amount().total();

        if (!storeMatches || !channelMatches || !isTestChannel || !currencyMatches || !amountMatches) {
            log.warn("결제 검증 실패. paymentId={}, storeMatches={}, channelMatches={}, isTestChannel={}, currencyMatches={}, amountMatches={}",
                    payment.getPaymentId(), storeMatches, channelMatches, isTestChannel, currencyMatches, amountMatches);
            throw new ApiException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
    }

    // 같은 PortOne 거래(transactionId)는 완료 API·웹훅이 중복 호출돼도 한 번만 기록한다.
    private void recordTransaction(Payment payment, PortOnePaymentResponse remote) {
        if (remote.transactionId() == null
                || paymentTransactionRepository.findByTransactionId(remote.transactionId()).isPresent()) {
            return;
        }

        PaymentStatus transactionStatus = switch (remote.status()) {
            case PORTONE_STATUS_PAID -> PaymentStatus.PAID;
            case PORTONE_STATUS_FAILED -> PaymentStatus.FAILED;
            case PORTONE_STATUS_VIRTUAL_ACCOUNT_ISSUED -> PaymentStatus.VIRTUAL_ACCOUNT_ISSUED;
            default -> throw new ApiException(PaymentErrorCode.UNEXPECTED_PAYMENT_STATUS);
        };

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .transactionId(remote.transactionId())
                .status(transactionStatus)
                .amount(remote.amount() != null ? remote.amount().total() : 0L)
                .failureReason(remote.failure() != null ? remote.failure().reason() : null)
                .approvedAt(remote.paidAt() != null ? LocalDateTime.ofInstant(remote.paidAt(), ZoneOffset.UTC) : null)
                .build());
    }

    private boolean isFinalized(PaymentStatus status) {
        return status == PaymentStatus.PAID || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED;
    }

    private PaymentCompleteResponse toCompleteResponse(Payment payment) {
        return new PaymentCompleteResponse(payment.getPaymentId(), payment.getStatus().name(), payment.totalAmount());
    }

    private ReservationForPaymentResponse getReservationOrThrow(Long reservationId) {
        try {
            return reservationServiceClient.getReservation(reservationId);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(PaymentErrorCode.RESERVATION_NOT_FOUND);
        } catch (RestClientException e) {
            throw new ApiException(PaymentErrorCode.RESERVATION_SERVICE_UNAVAILABLE);
        }
    }
}
