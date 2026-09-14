package org.example.paymentservice.domain.settlement;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.domain.settlement.dto.*;
import org.example.paymentservice.domain.payment.*;
import org.example.paymentservice.domain.cancellation.*;
import org.example.paymentservice.infrastructure.reservation.*;
import org.example.paymentservice.infrastructure.reservation.dto.*;
import org.example.paymentservice.infrastructure.portone.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.example.paymentservice.common.exception.ApiException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @org.springframework.context.annotation.DependsOn("settlementLedgerInitializer")
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
    @Value("${portone.store-id}") private String storeId;

    public record Actor(Long id, String role) { }
    public record Command(Instant paidAt, String paymentReference, String memo) { }
    public SettlementDetailResponse detail(Actor actor, boolean host, Long id) {
        return queries.detail(new SettlementActor(actor.id(), actor.role()), host, id);
    }
    public SettlementDetailResponse command(Actor actor, Long id, String action, String key, Command command) {
        return command(new SettlementActor(actor.id(), actor.role()), id, action, key,
                new SettlementCommandRequest(command.paidAt(), command.paymentReference(), command.memo()));
    }

    private record Evidence(Payment payment, ReservationForPaymentResponse reservation,
                            PaymentMethodCategory method, Instant paidAt, SettlementCalculator.Result result) { }
    private record Calculation(FestivalSettlementClient.Context festival, List<Evidence> evidence, String hold) { }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void refreshHostNames() {
        var missing = repository.findByRetiredFalseOrderByIdAsc().stream().filter(s -> s.getHostName() == null).toList();
        var names = hosts.names(missing.stream().map(Settlement::getHostUserId).toList());
        if (names.isEmpty()) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (var s : missing) repository.findById(s.getId()).orElseThrow().snapshotHostName(names.get(s.getHostUserId()));
        });
    }
    private Calculation gather(Long festivalId, boolean test) {
        var context = festivals.context(festivalId);
        if (context.hostUserId() == null || context.eligibleAt() == null) return new Calculation(context, List.of(), "MISSING_FESTIVAL_CONTEXT");
        if (!Set.of("PUBLISHED", "CLOSED", "CANCELLED").contains(context.status())) return new Calculation(context, List.of(), "ORGANIZER_REFUND_PENDING");
        var reservationRows = reservations.settlementContext(festivalId);
        var byId = new HashMap<Long, ReservationForPaymentResponse>(); reservationRows.forEach(r -> byId.put(r.reservationId(), r));
        var paymentRows = payments.findByReservationIdIn(byId.keySet());
        var evidence = new ArrayList<Evidence>();
        try {
            for (var reservation : reservationRows) {
                if (Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED").contains(reservation.status()) &&
                        paymentRows.stream().noneMatch(p -> p.getPaymentId().equals(reservation.paymentId())))
                    throw new IllegalArgumentException("MISSING_PAYMENT");
            }
            for (Payment payment : paymentRows) {
                if (!Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED).contains(payment.getStatus())) continue;
                var reservation = byId.get(payment.getReservationId());
                var remote = portone.getPayment(payment.getPaymentId());
                if (remote == null || remote.channel() == null || !Set.of("TEST", "LIVE").contains(remote.channel().type()))
                    throw new IllegalArgumentException("UNKNOWN_TEST_CHANNEL");
                // 채널 구분 없이 승인된 결제를 동일한 정산에 포함한다.
                if (remote.amount() == null || remote.amount().total() != payment.getTicketAmount() || !"KRW".equals(remote.currency())
                        || !payment.getPaymentId().equals(remote.id()) || !storeId.equals(remote.storeId())
                        || !Set.of("PAID", "PARTIAL_CANCELLED", "CANCELLED").contains(remote.status())
                        || remote.paidAt() == null || reservation.refundedQuantity() == null || reservation.unitPrice() == null
                        || !payment.getPaymentId().equals(reservation.paymentId()) || reservation.totalAmount() != payment.getTicketAmount())
                    throw new IllegalArgumentException("PAYMENT_RESERVATION_MISMATCH");
                var method = PaymentMethodCategory.fromRaw(remote.method() == null ? null : remote.method().type());
                var refunds = new ArrayList<SettlementCalculator.Refund>();
                var local = cancellations.findByPayment(payment);
                for (var c : local) {
                    if (c.getStatus() == CancellationStatus.SUCCEEDED && c.getGrossAmount() == null)
                        throw new IllegalArgumentException("MISSING_REFUND_SNAPSHOT");
                    refunds.add(new SettlementCalculator.Refund(c.getGrossAmount() == null ? 0 : c.getGrossAmount(), c.getAmount(),
                            c.getQuantity(), c.getStatus() == CancellationStatus.SUCCEEDED,
                            c.getStatus() == CancellationStatus.REQUESTED || c.getStatus() == CancellationStatus.PENDING));
                }
                if (remote.cancellations() != null) for (var c : remote.cancellations()) {
                    if (Set.of("REQUESTED", "PENDING").contains(c.status())) throw new IllegalArgumentException("CANCELLATION_PENDING");
                    if ("SUCCEEDED".equals(c.status()) && local.stream().noneMatch(l -> Objects.equals(l.getCancellationId(), c.id())
                            && l.getStatus() == CancellationStatus.SUCCEEDED && l.getAmount() == c.totalAmount()))
                        throw new IllegalArgumentException("UNKNOWN_EXTERNAL_CANCELLATION");
                }
                var result = SettlementCalculator.calculate(new SettlementCalculator.Input(payment.getTicketAmount(),
                        reservation.unitPrice(), reservation.quantity(), reservation.refundedQuantity(), method, refunds, remote.amount().cancelled()));
                evidence.add(new Evidence(payment, reservation, method, remote.paidAt(), result));
            }
            return new Calculation(context, evidence, null);
        // 산출 중 발견한 불일치는 예외가 아니라 보류 사유로 변환한다.
        } catch (IllegalArgumentException e) { return new Calculation(context, List.of(), e.getMessage()); }
    }
    public void calculateFestival(Long festivalId, boolean test) {
        var existing = repository.findByActiveFestivalId(festivalId);
        if (existing.isPresent() && (!existing.get().getStatus().recalculable() || existing.get().isManualHold())) return;
        Calculation calculation = gather(festivalId, test);
        if (calculation.festival().eligibleAt() == null || calculation.festival().hostUserId() == null)
            throw new ApiException(SettlementErrorCode.MISSING_FESTIVAL_CONTEXT);
        var hostNames = hosts.names(List.of(calculation.festival().hostUserId()));
        if (calculation.festival().eligibleAt().isAfter(Instant.now())) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findByActiveFestivalId(festivalId).orElseGet(() -> repository.saveAndFlush(
                    new Settlement(festivalId, calculation.festival().hostUserId(), calculation.festival().name(), calculation.festival().eligibleAt(), test)));
            s.snapshotHostName(hostNames.get(s.getHostUserId()));
            if (!s.getStatus().recalculable() || s.isManualHold()) return;
            SettlementStatus previous = s.getStatus();
            if (calculation.hold() != null) { restoreAllocations(s.getId()); s.hold(calculation.hold(), false); }
            else {
                s.clearLines(); repository.flush();
                restoreAllocations(s.getId());
                long adjustment = 0;
                long available = calculation.evidence().stream().mapToLong(e -> e.result().payout()).sum();
                var receivables = adjustments.findByHostUserIdAndRemainingAmountNot(s.getHostUserId(), 0).stream()
                        .sorted(Comparator.comparingLong(SettlementAdjustment::getRemainingAmount).reversed()).toList();
                for (var a : receivables) {
                    if (a.getSourceSettlementId().equals(s.getId())) continue;
                    long applied = a.getRemainingAmount() > 0 ? a.getRemainingAmount() : Math.max(a.getRemainingAmount(), -available);
                    if (applied == 0) continue;
                    a.allocate(applied); allocations.save(new SettlementAdjustmentAllocation(a.getId(), s.getId(), applied));
                    adjustment = Math.addExact(adjustment, applied); available = Math.addExact(available, applied);
                }
                var lines = calculation.evidence().stream().map(e -> new SettlementLine(s, e.payment().getId(),
                        e.reservation().reservationId(), e.reservation().ticketTypeId(), e.paidAt(), e.method(), e.result())).toList();
                s.calculate(lines, adjustment);
            }
            audits.save(new SettlementAuditLog(s, "CALCULATE", previous, null, calculation.hold(), null, null));
        });
    }
    public SettlementDetailResponse command(SettlementActor actor, Long id, String action, String key, SettlementCommandRequest command) {
        actor.require("ADMIN");
        if (key == null || key.isBlank() || key.length() > 100) throw new ApiException(SettlementErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        String fingerprint = UUID.nameUUIDFromBytes((id + ":" + actor.id() + ":" + action + ":" + command.fingerprintValue()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var prior = audits.findByCommandKey(key);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getCommandFingerprint())) throw new ApiException(SettlementErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            return queries.detail(actor, false, id);
        }
        if ("reapprove".equals(action)) reconcileFrozen(id);
        Settlement before = queries.owned(actor, false, id);
        if ("recalculate".equals(action)) {
            if (!before.getStatus().recalculable() || before.isManualHold()) throw new ApiException(SettlementErrorCode.RECALCULATION_BLOCKED);
            calculateFestival(before.getFestivalId(), before.isTestPayment()); return queries.detail(actor, false, id);
        }
        Calculation check = Set.of("confirm", "mark-paid", "reapprove").contains(action) ? gather(before.getFestivalId(), before.isTestPayment()) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            if (!Objects.equals(before.getVersion(), s.getVersion())) throw new ApiException(SettlementErrorCode.SETTLEMENT_VERSION_CONFLICT);
            if (check != null) {
                if (check.festival().eligibleAt().isAfter(Instant.now())) throw new ApiException(SettlementErrorCode.SETTLEMENT_NOT_ELIGIBLE);
                if (check.hold() != null || check.evidence().size() < s.getLines().size()) throw new ApiException(SettlementErrorCode.RECONCILIATION_REQUIRED);
                for (var current : check.evidence()) {
                    var line = s.getLines().stream().filter(l -> l.getPaymentId().equals(current.payment().getId())).findFirst();
                    var changes = adjustments.findBySourceSettlementIdAndPaymentId(id, current.payment().getId());
                    var latest = changes.stream().max(Comparator.comparing(SettlementAdjustment::getId));
                    long expectedPayout = line.map(SettlementLine::getPayoutAmount).orElse(0L) + changes.stream().mapToLong(SettlementAdjustment::getAmount).sum();
                    long expectedFace = latest.map(SettlementAdjustment::getRefundedFaceAmount).orElse(line.map(SettlementLine::getRefundedFaceAmount).orElse(0L));
                    long expectedCash = latest.map(SettlementAdjustment::getCustomerRefundAmount).orElse(line.map(SettlementLine::getCustomerRefundAmount).orElse(0L));
                    var locked = payments.findLockedById(current.payment().getId()).orElseThrow();
                    if (!Objects.equals(locked.getVersion(), current.payment().getVersion())) throw new ApiException(SettlementErrorCode.PAYMENT_CHANGED);
                    var localRefunds = cancellations.findByPayment(locked);
                    if (localRefunds.stream().anyMatch(c -> c.getStatus() == CancellationStatus.PENDING || c.getStatus() == CancellationStatus.REQUESTED)
                            || localRefunds.stream().filter(c -> c.getStatus() == CancellationStatus.SUCCEEDED)
                                .mapToLong(Cancellation::getAmount).sum() != current.result().cash()) throw new ApiException(SettlementErrorCode.REFUND_CHANGED);
                    if (current.result().payout() != expectedPayout || current.result().face() != expectedFace
                            || current.result().cash() != expectedCash) throw new ApiException(SettlementErrorCode.RECONCILIATION_REQUIRED);
                }
                long adjustment = adjustments.findBySourceSettlementId(id).stream().filter(a -> "PRE_PAYMENT".equals(a.getKind()))
                        .mapToLong(SettlementAdjustment::getAmount).sum();
                if ("mark-paid".equals(action) && adjustment != s.getConfirmedAdjustmentAmount()) throw new ApiException(SettlementErrorCode.REAPPROVAL_REQUIRED);
            }
            validateCommand(s, action, command);
            var previous = s.getStatus();
            switch (action) {
                case "confirm" -> s.confirm();
                case "reapprove" -> reapprove(s);
                case "mark-paid" -> s.markPaid(command.paidAt(), command.paymentReference(), command.memo());
                case "hold" -> { restoreAllocations(s.getId()); s.hold("MANUAL_REVIEW", true); }
                case "release" -> s.release();
                default -> throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
            }
            audits.save(new SettlementAuditLog(s, action, previous, actor.id(), command.memo(), key, fingerprint));
        });
        if ("release".equals(action)) calculateFestival(before.getFestivalId(), before.isTestPayment());
        return queries.detail(actor, false, id);
    }
    public void reconcileFrozen(Long id) {
        Settlement before = repository.findById(id).orElseThrow();
        if (before.isRetired()) return;
        if (before.getStatus().recalculable()) throw new ApiException(SettlementErrorCode.SETTLEMENT_NOT_FROZEN);
        Calculation calculation = gather(before.getFestivalId(), before.isTestPayment());
        if (calculation.hold() != null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            for (var current : calculation.evidence()) {
                Long paymentId = current.payment().getId();
                long original = s.getLines().stream().filter(l -> l.getPaymentId().equals(paymentId)).mapToLong(SettlementLine::getPayoutAmount).sum();
                long applied = adjustments.findBySourceSettlementIdAndPaymentId(id, paymentId).stream().mapToLong(SettlementAdjustment::getAmount).sum();
                long delta = current.result().payout() - original - applied;
                if (delta != 0) {
                    adjustments.save(new SettlementAdjustment(s, paymentId, current.result().face(), current.result().cash(), delta));
                    var previous = s.getStatus();
                    if (s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED) s.transition(SettlementStatus.ADJUSTMENT_REQUIRED);
                    audits.save(new SettlementAuditLog(s, "ADJUSTMENT", previous, null,
                            s.getPaidAt() == null ? "확정 후 변경으로 지급 검토 필요" : "지급 후 차액 조정 생성", null, null));
                }
            }
        });
    }
    private void restoreAllocations(Long settlementId) {
        var previous = allocations.findBySettlementId(settlementId);
        for (var allocation : previous) adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(allocation.getAmount());
        allocations.deleteAll(previous); allocations.flush();
    }
    // 엔티티의 최후 방어선에 도달하기 전에 운영자가 이해할 수 있는 업무 오류로 응답한다.
    private void validateCommand(Settlement s, String action, SettlementCommandRequest command) {
        boolean allowed = switch (action) {
            case "confirm" -> s.getStatus() == SettlementStatus.CALCULATED;
            case "reapprove" -> s.getStatus() == SettlementStatus.ADJUSTMENT_REQUIRED && s.getPaidAt() == null;
            case "mark-paid" -> s.getStatus() == SettlementStatus.CONFIRMED;
            case "hold" -> s.getStatus().permits(SettlementStatus.HELD);
            case "release" -> s.getStatus() == SettlementStatus.HELD;
            default -> throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
        };
        if (!allowed) throw new ApiException("reapprove".equals(action)
                ? SettlementErrorCode.REAPPROVAL_BLOCKED : SettlementErrorCode.SETTLEMENT_STATE_CONFLICT);
        if ("confirm".equals(action) && s.getPayoutAmount() < 0)
            throw new ApiException(SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED);
        if ("mark-paid".equals(action) && (command.paidAt() == null || command.paidAt().isAfter(Instant.now())
                || command.paymentReference() == null || command.paymentReference().isBlank()))
            throw new ApiException(SettlementErrorCode.PAYMENT_REFERENCE_REQUIRED);
    }

    private void reapprove(Settlement s) {
        if (s.getPaidAt() != null || s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED)
            throw new ApiException(SettlementErrorCode.REAPPROVAL_BLOCKED);
        long correction = adjustments.findBySourceSettlementId(s.getId()).stream()
                .filter(a -> "PRE_PAYMENT".equals(a.getKind())).mapToLong(SettlementAdjustment::getAmount).sum();
        long deficit = Math.max(0, -Math.addExact(s.getPayoutAmount(), correction));
        long released = 0;
        for (var allocation : allocations.findBySettlementId(s.getId())) {
            if (deficit == 0) break;
            if (allocation.getAmount() >= 0) continue;
            long restore = Math.min(deficit, -allocation.getAmount());
            adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(-restore);
            allocation.releaseDebt(restore); released = Math.addExact(released, restore); deficit -= restore;
        }
        if (deficit != 0) throw new ApiException(SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED);
        if (released > 0) adjustments.save(new SettlementAdjustment(s, null, 0, 0, released));
        s.reapprove(Math.addExact(correction, released));
    }
}
