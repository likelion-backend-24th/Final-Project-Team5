package org.example.paymentservice.domain.settlement;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.cancellation.Cancellation;
import org.example.paymentservice.domain.cancellation.CancellationRepository;
import org.example.paymentservice.domain.cancellation.CancellationStatus;
import org.example.paymentservice.domain.payment.Payment;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import org.example.paymentservice.domain.payment.PaymentRepository;
import org.example.paymentservice.domain.payment.PaymentStatus;
import org.example.paymentservice.domain.settlement.dto.SettlementActor;
import org.example.paymentservice.domain.settlement.dto.SettlementCommandRequest;
import org.example.paymentservice.domain.settlement.dto.SettlementDetailResponse;
import org.example.paymentservice.infrastructure.portone.PortOnePaymentClient;
import org.example.paymentservice.infrastructure.reservation.ReservationServiceClient;
import org.example.paymentservice.infrastructure.reservation.dto.ReservationForPaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@DependsOn("settlementLedgerInitializer")
public class SettlementService {

    private final SettlementQueryService queries;

    private final SettlementRepository repository;

    private final SettlementAdjustmentRepository adjustments;

    private final SettlementAdjustmentAllocationRepository allocations;

    private final SettlementAuditLogRepository audits;

    private final PaymentRepository payments;

    private final CancellationRepository cancellations;

    private final ReservationServiceClient reservations;

    private final FestivalSettlementClient festivals;

    private final PortOnePaymentClient portone;

    private final PlatformTransactionManager transactionManager;

    private final SettlementHostClient hosts;

    @Value("${portone.store-id}")
    private String storeId;

    public record Actor(Long id, String role) {}

    public record Command(Instant paidAt, String paymentReference, String memo) {}

    public SettlementDetailResponse detail(Actor actor, boolean host, Long id) {
        return queries.detail(new SettlementActor(actor.id(), actor.role()), host, id);
    }

    public SettlementDetailResponse command(Actor actor, Long id, String action, String key, Command command) {
        return command(
            new SettlementActor(actor.id(), actor.role()),
            id,
            action,
            key,
            new SettlementCommandRequest(command.paidAt(), command.paymentReference(), command.memo())
        );
    }

    private record Evidence(
        Payment payment,
        ReservationForPaymentResponse reservation,
        PaymentMethodCategory method,
        Instant paidAt,
        SettlementCalculator.Result result
    ) {}

    private record Calculation(FestivalSettlementClient.Context festival, List<Evidence> evidence, String hold) {}

    @EventListener(ApplicationReadyEvent.class)
    public void refreshHostNames() {
        var missing = repository
            .findByRetiredFalseOrderByIdAsc()
            .stream()
            .filter(s -> s.getHostName() == null)
            .toList();
        var names = hosts.names(missing.stream().map(Settlement::getHostUserId).toList());
        if (names.isEmpty()) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (var s : missing)
                repository.findById(s.getId()).orElseThrow().snapshotHostName(names.get(s.getHostUserId()));
        });
    }

    private Calculation gather(Long festivalId, boolean test) {
        var context = festivals.context(festivalId);
        // 소유자나 정산 가능 시각이 없으면 지급 대상과 유예기간을 확정할 수 없다.
        if (context.hostUserId() == null || context.eligibleAt() == null) return new Calculation(
            context,
            List.of(),
            "MISSING_FESTIVAL_CONTEXT"
        );
        // 행사 취소 환불이 끝나기 전에는 지급 가능한 매출을 확정하지 않는다.
        if (!Set.of("PUBLISHED", "CLOSED", "CANCELLED").contains(context.status())) return new Calculation(
            context,
            List.of(),
            "ORGANIZER_REFUND_PENDING"
        );
        var reservationRows = reservations.settlementContext(festivalId);
        var byId = new HashMap<Long, ReservationForPaymentResponse>();
        reservationRows.forEach(r -> byId.put(r.reservationId(), r));
        var paymentRows = payments.findByReservationIdIn(byId.keySet());
        var evidence = new ArrayList<Evidence>();
        try {
            for (var reservation : reservationRows) {
                // 유효한 예약에 결제 근거가 없으면 누락된 매출을 추정하지 않는다.
                if (
                    Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(reservation.status()) &&
                    paymentRows.stream().noneMatch(p -> p.getPaymentId().equals(reservation.paymentId()))
                ) throw new IllegalArgumentException("MISSING_PAYMENT");
            }
            for (Payment payment : paymentRows) {
                if (
                    !Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED).contains(
                        payment.getStatus()
                    )
                ) continue;
                var reservation = byId.get(payment.getReservationId());
                var remote = portone.getPayment(payment.getPaymentId());
                // 채널 출처가 확인되지 않은 결제를 승인 거래로 가정하지 않는다.
                if (
                    remote == null ||
                    remote.channel() == null ||
                    !Set.of("TEST", "LIVE").contains(remote.channel().type())
                ) throw new IllegalArgumentException("UNKNOWN_TEST_CHANNEL");
                // 채널 구분 없이 승인된 결제를 동일한 정산에 포함한다.
                // PG와 예약의 식별자·통화·금액·수량 근거가 일치해야 중복 또는 오지급을 막는다.
                if (
                    remote.amount() == null ||
                    remote.amount().total() != payment.getTicketAmount() ||
                    !"KRW".equals(remote.currency()) ||
                    !payment.getPaymentId().equals(remote.id()) ||
                    !storeId.equals(remote.storeId()) ||
                    !Set.of("PAID", "PARTIAL_CANCELLED", "CANCELLED").contains(remote.status()) ||
                    remote.paidAt() == null ||
                    reservation.refundedQuantity() == null ||
                    reservation.unitPrice() == null ||
                    !payment.getPaymentId().equals(reservation.paymentId()) ||
                    reservation.totalAmount() != payment.getTicketAmount()
                ) throw new IllegalArgumentException("PAYMENT_RESERVATION_MISMATCH");
                var method = PaymentMethodCategory.fromRaw(remote.method() == null ? null : remote.method().type());
                var refunds = new ArrayList<SettlementCalculator.Refund>();
                var local = cancellations.findByPayment(payment);
                for (var c : local) {
                    // 실제 환급액만으로 환불 티켓 액면가와 수수료 환입을 역산하지 않는다.
                    if (
                        c.getStatus() == CancellationStatus.SUCCEEDED && c.getGrossAmount() == null
                    ) throw new IllegalArgumentException("MISSING_REFUND_SNAPSHOT");
                    refunds.add(
                        new SettlementCalculator.Refund(
                            c.getGrossAmount() == null ? 0 : c.getGrossAmount(),
                            c.getAmount(),
                            c.getQuantity(),
                            c.getStatus() == CancellationStatus.SUCCEEDED,
                            c.getStatus() == CancellationStatus.REQUESTED || c.getStatus() == CancellationStatus.PENDING
                        )
                    );
                }
                if (remote.cancellations() != null) for (var c : remote.cancellations()) {
                    // 완료되지 않은 취소는 최종 환급액을 바꿀 수 있다.
                    if (Set.of("REQUESTED", "PENDING").contains(c.status())) throw new IllegalArgumentException(
                        "CANCELLATION_PENDING"
                    );
                    // PG 취소에 대응하는 성공한 내부 근거가 없으면 환불 수량을 확정할 수 없다.
                    if (
                        "SUCCEEDED".equals(c.status()) &&
                        local
                            .stream()
                            .noneMatch(
                                l ->
                                    Objects.equals(l.getCancellationId(), c.id()) &&
                                    l.getStatus() == CancellationStatus.SUCCEEDED &&
                                    l.getAmount() == c.totalAmount()
                            )
                    ) throw new IllegalArgumentException("UNKNOWN_EXTERNAL_CANCELLATION");
                }
                var result = SettlementCalculator.calculate(
                    new SettlementCalculator.Input(
                        payment.getTicketAmount(),
                        reservation.unitPrice(),
                        reservation.quantity(),
                        reservation.refundedQuantity(),
                        method,
                        refunds,
                        remote.amount().cancelled()
                    )
                );
                evidence.add(new Evidence(payment, reservation, method, remote.paidAt(), result));
            }
            return new Calculation(context, evidence, null);
            // 산출 중 발견한 불일치는 예외가 아니라 보류 사유로 변환한다.
        } catch (IllegalArgumentException e) {
            return new Calculation(context, List.of(), e.getMessage());
        }
    }

    public void calculateFestival(Long festivalId, boolean test) {
        // 수동 병합 대상에 새 원장을 만들면 기존 확정 금액을 중복 집계할 수 있다.
        if (repository.existsByFestivalIdAndActiveFestivalIdIsNullAndRetiredFalse(festivalId)) {
            return;
        }
        var existing = repository.findByActiveFestivalId(festivalId);
        if (
            existing.isPresent() && (!existing.get().getStatus().recalculable() || existing.get().isManualHold())
        ) return;
        Calculation calculation = gather(festivalId, test);
        if (
            calculation.festival().eligibleAt() == null || calculation.festival().hostUserId() == null
        ) throw new ApiException(SettlementErrorCode.MISSING_FESTIVAL_CONTEXT);
        var hostNames = hosts.names(List.of(calculation.festival().hostUserId()));
        if (calculation.festival().eligibleAt().isAfter(Instant.now())) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository
                .findByActiveFestivalId(festivalId)
                .orElseGet(() ->
                    repository.saveAndFlush(
                        new Settlement(
                            festivalId,
                            calculation.festival().hostUserId(),
                            calculation.festival().name(),
                            calculation.festival().eligibleAt(),
                            test
                        )
                    )
                );
            s.snapshotHostName(hostNames.get(s.getHostUserId()));
            if (!s.getStatus().recalculable() || s.isManualHold()) return;
            SettlementStatus previous = s.getStatus();
            if (calculation.hold() != null) {
                restoreAllocations(s.getId());
                s.hold(calculation.hold(), false);
            } else {
                s.clearLines();
                repository.flush();
                restoreAllocations(s.getId());
                long adjustment = 0;
                long available = calculation
                    .evidence()
                    .stream()
                    .mapToLong(e -> e.result().payout())
                    .sum();
                var receivables = adjustments
                    .findByHostUserIdAndRemainingAmountNot(s.getHostUserId(), 0)
                    .stream()
                    .sorted(Comparator.comparingLong(SettlementAdjustment::getRemainingAmount).reversed())
                    .toList();
                // 남은 지급액까지만 미수금을 상계하고 부족분은 다음 정산을 위해 보존한다.
                for (var a : receivables) {
                    if (a.getSourceSettlementId().equals(s.getId())) continue;
                    long applied =
                        a.getRemainingAmount() > 0
                            ? a.getRemainingAmount()
                            : Math.max(a.getRemainingAmount(), -available);
                    if (applied == 0) continue;
                    a.allocate(applied);
                    allocations.save(new SettlementAdjustmentAllocation(a.getId(), s.getId(), applied));
                    adjustment = Math.addExact(adjustment, applied);
                    available = Math.addExact(available, applied);
                }
                var lines = calculation
                    .evidence()
                    .stream()
                    .map(e ->
                        new SettlementLine(
                            s,
                            e.payment().getId(),
                            e.reservation().reservationId(),
                            e.reservation().ticketTypeId(),
                            e.paidAt(),
                            e.method(),
                            e.result()
                        )
                    )
                    .toList();
                s.calculate(lines, adjustment);
            }
            audits.save(new SettlementAuditLog(s, "CALCULATE", previous, null, calculation.hold(), null, null));
        });
    }

    public SettlementDetailResponse command(
        SettlementActor actor,
        Long id,
        String action,
        String key,
        SettlementCommandRequest command
    ) {
        actor.require("ADMIN");
        var operation = SettlementAction.fromPath(action);
        if (key == null || key.isBlank() || key.length() > 100) throw new ApiException(
            SettlementErrorCode.IDEMPOTENCY_KEY_REQUIRED
        );
        // 같은 멱등 키의 재시도와 다른 내용의 재사용을 구별하며 기존 입력 형식을 유지한다.
        String fingerprint = UUID.nameUUIDFromBytes(
            (id + ":" + actor.id() + ":" + action + ":" + command.fingerprintValue()).getBytes(StandardCharsets.UTF_8)
        ).toString();
        var prior = audits.findByCommandKey(key);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getCommandFingerprint())) throw new ApiException(
                SettlementErrorCode.IDEMPOTENCY_KEY_CONFLICT
            );
            return queries.detail(actor, false, id);
        }
        if ("reapprove".equals(action)) reconcileFrozen(id);
        Settlement before = queries.owned(actor, false, id);
        if ("recalculate".equals(action)) {
            if (!before.getStatus().recalculable() || before.isManualHold()) throw new ApiException(
                SettlementErrorCode.RECALCULATION_BLOCKED
            );
            calculateFestival(before.getFestivalId(), before.isTestPayment());
            return queries.detail(actor, false, id);
        }
        Calculation check = Set.of("confirm", "mark-paid", "reapprove").contains(action)
            ? gather(before.getFestivalId(), before.isTestPayment())
            : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            if (!Objects.equals(before.getVersion(), s.getVersion())) throw new ApiException(
                SettlementErrorCode.SETTLEMENT_VERSION_CONFLICT
            );
            if (check != null) {
                if (check.festival().eligibleAt().isAfter(Instant.now())) throw new ApiException(
                    SettlementErrorCode.SETTLEMENT_NOT_ELIGIBLE
                );
                if (check.hold() != null || check.evidence().size() < s.getLines().size()) throw new ApiException(
                    SettlementErrorCode.RECONCILIATION_REQUIRED
                );
                for (var current : check.evidence()) {
                    var line = s
                        .getLines()
                        .stream()
                        .filter(l -> l.getPaymentId().equals(current.payment().getId()))
                        .findFirst();
                    var changes = adjustments.findBySourceSettlementIdAndPaymentId(id, current.payment().getId());
                    var latest = changes.stream().max(Comparator.comparing(SettlementAdjustment::getId));
                    long expectedPayout =
                        line.map(SettlementLine::getPayoutAmount).orElse(0L) +
                        changes.stream().mapToLong(SettlementAdjustment::getAmount).sum();
                    long expectedFace = latest
                        .map(SettlementAdjustment::getRefundedFaceAmount)
                        .orElse(line.map(SettlementLine::getRefundedFaceAmount).orElse(0L));
                    long expectedCash = latest
                        .map(SettlementAdjustment::getCustomerRefundAmount)
                        .orElse(line.map(SettlementLine::getCustomerRefundAmount).orElse(0L));
                    var locked = payments.findLockedById(current.payment().getId()).orElseThrow();
                    if (!Objects.equals(locked.getVersion(), current.payment().getVersion())) throw new ApiException(
                        SettlementErrorCode.PAYMENT_CHANGED
                    );
                    var localRefunds = cancellations.findByPayment(locked);
                    if (
                        localRefunds
                            .stream()
                            .anyMatch(
                                c ->
                                    c.getStatus() == CancellationStatus.PENDING ||
                                    c.getStatus() == CancellationStatus.REQUESTED
                            ) ||
                        localRefunds
                            .stream()
                            .filter(c -> c.getStatus() == CancellationStatus.SUCCEEDED)
                            .mapToLong(Cancellation::getAmount)
                            .sum() != current.result().cash()
                    ) throw new ApiException(SettlementErrorCode.REFUND_CHANGED);
                    if (
                        current.result().payout() != expectedPayout ||
                        current.result().face() != expectedFace ||
                        current.result().cash() != expectedCash
                    ) throw new ApiException(SettlementErrorCode.RECONCILIATION_REQUIRED);
                }
                long adjustment = adjustments
                    .findBySourceSettlementId(id)
                    .stream()
                    .filter(a -> a.getKind() == SettlementAdjustmentKind.PRE_PAYMENT)
                    .mapToLong(SettlementAdjustment::getAmount)
                    .sum();
                if (
                    "mark-paid".equals(action) && adjustment != s.getConfirmedAdjustmentAmount()
                ) throw new ApiException(SettlementErrorCode.REAPPROVAL_REQUIRED);
            }
            validateCommand(s, action, command);
            var previous = s.getStatus();
            switch (operation) {
                case CONFIRM -> s.confirm();
                case REAPPROVE -> reapprove(s);
                case MARK_PAID -> s.markPaid(command.paidAt(), command.paymentReference(), command.memo());
                case HOLD -> {
                    restoreAllocations(s.getId());
                    s.hold("MANUAL_REVIEW", true);
                }
                case RELEASE -> s.release();
                default -> throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
            }
            audits.save(
                new SettlementAuditLog(s, operation.pathValue(), previous, actor.id(), command.memo(), key, fingerprint)
            );
        });
        if ("release".equals(action)) calculateFestival(before.getFestivalId(), before.isTestPayment());
        return queries.detail(actor, false, id);
    }

    public void reconcileFrozen(Long id) {
        Settlement before = repository.findById(id).orElseThrow();
        // 수동 병합이 필요한 중복 원장은 자동 대사로도 변경하지 않는다.
        if (before.isRetired() || before.getActiveFestivalId() == null) return;
        if (before.getStatus().recalculable()) throw new ApiException(SettlementErrorCode.SETTLEMENT_NOT_FROZEN);
        Calculation calculation = gather(before.getFestivalId(), before.isTestPayment());
        if (calculation.hold() != null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            for (var current : calculation.evidence()) {
                Long paymentId = current.payment().getId();
                long original = s
                    .getLines()
                    .stream()
                    .filter(l -> l.getPaymentId().equals(paymentId))
                    .mapToLong(SettlementLine::getPayoutAmount)
                    .sum();
                long applied = adjustments
                    .findBySourceSettlementIdAndPaymentId(id, paymentId)
                    .stream()
                    .mapToLong(SettlementAdjustment::getAmount)
                    .sum();
                // 이미 반영한 조정까지 제외한 차액만 저장해 반복 대사에서 중복 환입을 막는다.
                long delta = current.result().payout() - original - applied;
                if (delta != 0) {
                    adjustments.save(
                        new SettlementAdjustment(s, paymentId, current.result().face(), current.result().cash(), delta)
                    );
                    var previous = s.getStatus();
                    if (s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED) s.transition(
                        SettlementStatus.ADJUSTMENT_REQUIRED
                    );
                    audits.save(
                        new SettlementAuditLog(
                            s,
                            "ADJUSTMENT",
                            previous,
                            null,
                            s.getPaidAt() == null ? "확정 후 변경으로 지급 검토 필요" : "지급 후 차액 조정 생성",
                            null,
                            null
                        )
                    );
                }
            }
        });
    }

    private void restoreAllocations(Long settlementId) {
        var previous = allocations.findBySettlementId(settlementId);
        for (var allocation : previous)
            adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(allocation.getAmount());
        allocations.deleteAll(previous);
        allocations.flush();
    }

    // 엔티티의 최후 방어선에 도달하기 전에 운영자가 이해할 수 있는 업무 오류로 응답한다.
    private void validateCommand(Settlement s, String action, SettlementCommandRequest command) {
        boolean allowed = switch (SettlementAction.fromPath(action)) {
            case CONFIRM -> s.getStatus() == SettlementStatus.CALCULATED;
            case REAPPROVE -> s.getStatus() == SettlementStatus.ADJUSTMENT_REQUIRED && s.getPaidAt() == null;
            case MARK_PAID -> s.getStatus() == SettlementStatus.CONFIRMED;
            case HOLD -> s.getStatus().permits(SettlementStatus.HELD);
            case RELEASE -> s.getStatus() == SettlementStatus.HELD;
            default -> throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
        };
        if (!allowed) throw new ApiException(
            "reapprove".equals(action)
                ? SettlementErrorCode.REAPPROVAL_BLOCKED
                : SettlementErrorCode.SETTLEMENT_STATE_CONFLICT
        );
        if ("confirm".equals(action) && s.getPayoutAmount() < 0) throw new ApiException(
            SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED
        );
        if (
            "mark-paid".equals(action) &&
            (command.paidAt() == null ||
                command.paidAt().isAfter(Instant.now()) ||
                command.paymentReference() == null ||
                command.paymentReference().isBlank())
        ) throw new ApiException(SettlementErrorCode.PAYMENT_REFERENCE_REQUIRED);
    }

    private void reapprove(Settlement s) {
        if (s.getPaidAt() != null || s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED) throw new ApiException(
            SettlementErrorCode.REAPPROVAL_BLOCKED
        );
        long correction = adjustments
            .findBySourceSettlementId(s.getId())
            .stream()
            .filter(a -> a.getKind() == SettlementAdjustmentKind.PRE_PAYMENT)
            .mapToLong(SettlementAdjustment::getAmount)
            .sum();
        long deficit = Math.max(0, -Math.addExact(s.getPayoutAmount(), correction));
        long released = 0;
        // 이번 환불로 지급액이 부족하면 이전 미수금 상계를 먼저 풀어 이중 차감을 피한다.
        for (var allocation : allocations.findBySettlementId(s.getId())) {
            if (deficit == 0) break;
            if (allocation.getAmount() >= 0) continue;
            long restore = Math.min(deficit, -allocation.getAmount());
            adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(-restore);
            allocation.releaseDebt(restore);
            released = Math.addExact(released, restore);
            deficit -= restore;
        }
        if (deficit != 0) throw new ApiException(SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED);
        if (released > 0) adjustments.save(new SettlementAdjustment(s, null, 0, 0, released));
        s.reapprove(Math.addExact(correction, released));
    }
}
