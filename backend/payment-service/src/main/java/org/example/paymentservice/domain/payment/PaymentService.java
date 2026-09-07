package org.example.paymentservice.domain.payment;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareRequest;
import org.example.paymentservice.domain.payment.dto.PaymentPrepareResponse;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String RESERVATION_PAYABLE_STATUS = "PENDING";

    private final PaymentRepository paymentRepository;
    private final ReservationServiceClient reservationServiceClient;

    @Value("${payment.id-prefix}")
    private String paymentIdPrefix;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key.payment}")
    private String channelKeyPayment;

    // 참가자가 Pending 예매의 결제를 시작할 때: 예매 소유권·상태를 확인하고 Payment를 READY로 생성한다.
    @Transactional
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
                .ticketAmount(reservation.totalAmount())
                .platformFee(0L)
                .currency("KRW")
                .status(PaymentStatus.READY)
                .build();
        paymentRepository.save(payment);

        return new PaymentPrepareResponse(payment.getPaymentId(), storeId, channelKeyPayment, payment.totalAmount());
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
