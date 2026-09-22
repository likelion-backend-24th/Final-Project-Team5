package org.example.paymentservice.domain.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.domain.cancellation.Cancellation;
import org.example.paymentservice.domain.cancellation.CancellationRepository;
import org.example.paymentservice.domain.cancellation.CancellationStatus;
import org.example.paymentservice.infrastructure.portone.dto.PortOneCancelResponse;
import org.example.paymentservice.infrastructure.portone.dto.PortOnePaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 데모 자동 입금된 결제의 "PortOne 응답"을 로컬 기록으로 만들어 준다.
 * 실제 PortOne에는 가상계좌가 미입금 상태로 남아 있으므로, 조회·취소를 그대로 보내면 검증 실패·취소 거절이 난다.
 * PortOnePaymentClient가 호출 전에 여기를 먼저 보고, 데모 입금 결제면 실제 API 대신 이 응답을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class DemoDepositRemote {

    static final String DEMO_CANCELLATION_ID_PREFIX = "demo-";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String STATUS_PARTIAL_CANCELLED = "PARTIAL_CANCELLED";
    private static final String METHOD_VIRTUAL_ACCOUNT = "VIRTUAL_ACCOUNT";
    private static final String CHANNEL_TYPE_TEST = "TEST";

    private final PaymentRepository paymentRepository;
    private final CancellationRepository cancellationRepository;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key.payment}")
    private String channelKeyPayment;

    // 데모 입금 결제면 PAID 조회 응답을, 아니면 비어 있는 값을 돌려준다(그때는 실제 PortOne을 조회한다).
    public Optional<PortOnePaymentResponse> remoteView(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .filter(Payment::isDemoDeposited)
                .map(this::toRemote);
    }

    // 데모 입금 결제의 취소는 PortOne에 보내지 않고 바로 성공한 것으로 응답한다.
    // 취소 행 자체는 호출자가 저장하므로 여기서는 식별자·상태·시각만 만들어 준다.
    public Optional<PortOneCancelResponse> cancel(String paymentId, Long amount, String idempotencyKey) {
        return paymentRepository.findByPaymentId(paymentId)
                .filter(Payment::isDemoDeposited)
                .map(payment -> {
                    long cancelAmount = amount != null ? amount : payment.totalAmount() - cancelledTotal(payment);
                    return new PortOneCancelResponse(new PortOnePaymentResponse.Cancellation(
                            DEMO_CANCELLATION_ID_PREFIX + idempotencyKey, CancellationStatus.SUCCEEDED.name(), null,
                            cancelAmount, Instant.now(), Instant.now()));
                });
    }

    private PortOnePaymentResponse toRemote(Payment payment) {
        List<Cancellation> cancellations = demoCancellations(payment);
        long cancelled = cancellations.stream().mapToLong(Cancellation::getAmount).sum();
        String status = STATUS_PAID;
        if (cancelled >= payment.totalAmount()) {
            status = STATUS_CANCELLED;
        } else if (cancelled > 0) {
            status = STATUS_PARTIAL_CANCELLED;
        }
        return new PortOnePaymentResponse(
                payment.getPaymentId(),
                status,
                null,
                storeId,
                new PortOnePaymentResponse.Channel(null, channelKeyPayment, CHANNEL_TYPE_TEST, null, null),
                new PortOnePaymentResponse.Method(METHOD_VIRTUAL_ACCOUNT, null, null, null, null, null,
                        payment.getVirtualAccountIssuedAt()),
                new PortOnePaymentResponse.Amount(payment.totalAmount(), 0, 0, 0, 0, payment.totalAmount(), cancelled, 0),
                payment.getCurrency(),
                null,
                payment.getVirtualAccountIssuedAt(),
                payment.getDemoDepositedAt(),
                payment.getDemoDepositedAt(),
                payment.getDemoDepositedAt(),
                null,
                null,
                null,
                cancellations.stream()
                        .map(c -> new PortOnePaymentResponse.Cancellation(c.getCancellationId(), c.getStatus().name(),
                                c.getReason(), c.getAmount(), c.getCancelledAt(), c.getCancelledAt()))
                        .toList());
    }

    private long cancelledTotal(Payment payment) {
        return demoCancellations(payment).stream().mapToLong(Cancellation::getAmount).sum();
    }

    // 이 결제에서 데모 경로로 성공한 취소만 원격 취소 목록으로 돌려준다(REQUESTED로 남은 행은 아직 없는 취소다).
    private List<Cancellation> demoCancellations(Payment payment) {
        return cancellationRepository.findByPayment(payment).stream()
                .filter(c -> c.getCancellationId() != null
                        && c.getCancellationId().startsWith(DEMO_CANCELLATION_ID_PREFIX)
                        && c.getStatus() == CancellationStatus.SUCCEEDED)
                .toList();
    }
}
