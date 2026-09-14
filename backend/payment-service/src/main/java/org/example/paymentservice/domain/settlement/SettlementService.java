package org.example.paymentservice.domain.settlement;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.domain.payment.*;
import org.example.paymentservice.domain.cancellation.*;
import org.example.paymentservice.infrastructure.reservation.*;
import org.example.paymentservice.infrastructure.reservation.dto.*;
import org.example.paymentservice.infrastructure.portone.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class SettlementService {
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
    @Value("${settlement.allow-test-payments:false}") private boolean allowTestPayments;
    @Value("${portone.store-id}") private String storeId;

    public record Actor(Long id, String role) {
        public void require(String expected) {
            if (id == null || !expected.equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
    }
    public record Filter(Instant from, Instant to, SettlementStatus status, Long festivalId,
                         Long hostUserId, PaymentMethodCategory paymentMethod, boolean testPayment, String dateBasis) {
        public Filter(Instant from, Instant to, SettlementStatus status, Long festivalId,
                      Long hostUserId, PaymentMethodCategory paymentMethod, boolean testPayment) {
            this(from, to, status, festivalId, hostUserId, paymentMethod, testPayment, "SETTLEMENT_AT");
        }
        public Filter {
            if (!Set.of("PAID_AT", "SETTLEMENT_AT").contains(dateBasis)) throw new IllegalArgumentException("INVALID_DATE_BASIS");
            if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("INVALID_DATE_RANGE");
        }
    }
    public record Command(Instant paidAt, String paymentReference, String memo) { }
    private record Evidence(Payment payment, ReservationForPaymentResponse reservation,
                            PaymentMethodCategory method, Instant paidAt, SettlementCalculator.Result result) { }
    private record Calculation(FestivalSettlementClient.Context festival, List<Evidence> evidence, String hold) { }

    private void testAccess(boolean test) {
        if (test && !allowTestPayments) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TEST_PAYMENTS_DISABLED");
    }
    public Page<Map<String, Object>> list(Actor actor, boolean host, Filter filter, int page, int size) {
        actor.require(host ? "HOST" : "ADMIN"); testAccess(filter.testPayment());
        if (page < 0 || size < 1 || size > 100 || (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_FILTER");
        return new TransactionTemplate(transactionManager).execute(tx -> repository.findAll(spec(actor, host, filter),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(this::publicView));
    }
    private Specification<Settlement> spec(Actor actor, boolean host, Filter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("testPayment"), f.testPayment()));
            Long owner = host ? actor.id() : f.hostUserId();
            if (owner != null) predicates.add(cb.equal(root.get("hostUserId"), owner));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.festivalId() != null) predicates.add(cb.equal(root.get("festivalId"), f.festivalId()));
            if ("PAID_AT".equals(f.dateBasis()) && (f.from() != null || f.to() != null)) {
                var sub = query.subquery(Long.class); var line = sub.from(SettlementLine.class);
                var dates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                dates.add(cb.equal(line.get("settlement"), root));
                if (f.from() != null) dates.add(cb.greaterThanOrEqualTo(line.get("paidAt"), f.from()));
                if (f.to() != null) dates.add(cb.lessThan(line.get("paidAt"), f.to()));
                sub.select(line.get("id")).where(dates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                predicates.add(cb.exists(sub));
            } else {
                if (f.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), f.from()));
                if (f.to() != null) predicates.add(cb.lessThan(root.get("calculatedAt"), f.to()));
            }
            if (f.paymentMethod() != null) {
                var sub = query.subquery(Long.class); var line = sub.from(SettlementLine.class);
                sub.select(line.get("id")).where(cb.equal(line.get("settlement"), root), cb.equal(line.get("paymentMethod"), f.paymentMethod()));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    public Map<String, Object> summary(Actor actor, boolean host, Filter filter) {
        actor.require(host ? "HOST" : "ADMIN"); testAccess(filter.testPayment());
        return new TransactionTemplate(transactionManager).execute(tx -> {
            var rows = repository.findAll(spec(actor, host, filter));
            var result = new LinkedHashMap<String, Object>(); result.put("currency", "KRW");
            result.put("dateBasis", filter.dateBasis()); result.put("count", rows.size());
            result.put("grossPaymentAmount", rows.stream().mapToLong(Settlement::getGrossPaymentAmount).sum());
            result.put("customerRefundAmount", rows.stream().mapToLong(Settlement::getCustomerRefundAmount).sum());
            result.put("platformFeeAmount", rows.stream().mapToLong(Settlement::getPlatformFeeAmount).sum());
            result.put("payoutAmount", rows.stream().mapToLong(s -> Math.addExact(s.getPayoutAmount(), s.getConfirmedAdjustmentAmount())).sum());
            result.put("paidAmount", rows.stream().filter(s -> s.getPaidAt() != null).mapToLong(s -> s.getPaidPayoutAmount() == null ? s.getPayoutAmount() : s.getPaidPayoutAmount()).sum());
            result.put("heldCount", rows.stream().filter(s -> s.getStatus() == SettlementStatus.HELD).count());
            return result;
        });
    }
    public Map<String, Object> detail(Actor actor, boolean host, Long id) {
        actor.require(host ? "HOST" : "ADMIN");
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Settlement s = owned(actor, host, id); var result = publicView(s);
            result.put("adjustments", adjustments.findBySourceSettlementId(id).stream().map(a -> Map.of(
                    "amount", a.getAmount(), "remainingAmount", a.getRemainingAmount(), "status", a.getStatus(), "createdAt", a.getCreatedAt())).toList());
            result.put("proposedPayoutAmount", Math.addExact(s.getPayoutAmount(), adjustments.findBySourceSettlementId(id).stream()
                    .filter(a -> "PRE_PAYMENT".equals(a.getKind())).mapToLong(SettlementAdjustment::getAmount).sum()));
            result.put("lines", s.getLines().stream().map(line -> {
                var data = new LinkedHashMap<String, Object>();
                data.put("paymentMethod", line.getPaymentMethod()); data.put("paidAt", line.getPaidAt());
                data.put("grossAmount", line.getGrossAmount()); data.put("refundedFaceAmount", line.getRefundedFaceAmount());
                data.put("customerRefundAmount", line.getCustomerRefundAmount()); data.put("penaltyAmount", line.getPenaltyAmount());
                data.put("feeRateBps", line.getFeeRateBps()); data.put("initialFeeAmount", line.getInitialFeeAmount());
                data.put("feeReversalAmount", line.getFeeReversalAmount()); data.put("finalFeeAmount", line.getFinalFeeAmount());
                data.put("payoutAmount", line.getPayoutAmount());
                if (!host) { data.put("paymentId", line.getPaymentId()); data.put("reservationId", line.getReservationId()); }
                return data;
            }).toList());
            if (!host) { result.put("auditLogs", audits.findBySettlementIdOrderByIdAsc(id)); result.put("adminMemo", s.getAdminMemo());
                result.put("paymentReference", s.getPaymentReference()); result.put("holdReason", s.getHoldReason()); }
            return result;
        });
    }
    private Settlement owned(Actor actor, boolean host, Long id) {
        Settlement s = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (host && !actor.id().equals(s.getHostUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        testAccess(s.isTestPayment()); return s;
    }
    private Map<String, Object> publicView(Settlement s) {
        var data = new LinkedHashMap<String, Object>();
        data.put("id", s.getId()); data.put("version", s.getVersion()); data.put("festivalId", s.getFestivalId());
        data.put("festivalName", s.getFestivalName()); data.put("hostUserId", s.getHostUserId()); data.put("currency", "KRW");
        data.put("status", s.getStatus()); data.put("testPayment", s.isTestPayment());
        data.put("eligibleAt", s.getEligibleAt()); data.put("calculatedAt", s.getCalculatedAt());
        data.put("confirmedAt", s.getConfirmedAt()); data.put("paidAt", s.getPaidAt());
        data.put("grossPaymentAmount", s.getGrossPaymentAmount()); data.put("grossRefundedFaceAmount", s.getGrossRefundedFaceAmount());
        data.put("customerRefundAmount", s.getCustomerRefundAmount()); data.put("cancellationPenaltyAmount", s.getCancellationPenaltyAmount());
        data.put("netTicketSalesAmount", s.getNetTicketSalesAmount()); data.put("platformFeeAmount", s.getPlatformFeeAmount());
        data.put("adjustmentAmount", s.getAdjustmentAmount()); data.put("payoutAmount", s.getPayoutAmount());
        data.put("confirmedAdjustmentAmount", s.getConfirmedAdjustmentAmount());
        data.put("payableAmount", Math.addExact(s.getPayoutAmount(), s.getConfirmedAdjustmentAmount()));
        data.put("paidPayoutAmount", s.getPaidPayoutAmount()); data.put("reapprovedAt", s.getReapprovedAt());
        data.put("holdMessage", s.getStatus() == SettlementStatus.HELD ? "결제·환불 내역을 확인 중입니다." : null);
        return data;
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
                if (test != "TEST".equals(remote.channel().type())) continue;
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
        } catch (IllegalArgumentException e) { return new Calculation(context, List.of(), e.getMessage()); }
    }
    public void calculateFestival(Long festivalId, boolean test) {
        testAccess(test);
        var existing = repository.findByFestivalIdAndTestPayment(festivalId, test);
        if (existing.isPresent() && (!existing.get().getStatus().recalculable() || existing.get().isManualHold())) return;
        Calculation calculation = gather(festivalId, test);
        if (calculation.festival().eligibleAt() == null || calculation.festival().hostUserId() == null)
            throw new IllegalStateException("MISSING_FESTIVAL_CONTEXT");
        if (calculation.festival().eligibleAt().isAfter(Instant.now())) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findByFestivalIdAndTestPayment(festivalId, test).orElseGet(() -> repository.saveAndFlush(
                    new Settlement(festivalId, calculation.festival().hostUserId(), calculation.festival().name(), calculation.festival().eligibleAt(), test)));
            if (!s.getStatus().recalculable() || s.isManualHold()) return;
            SettlementStatus previous = s.getStatus();
            if (calculation.hold() != null) { restoreAllocations(s.getId()); s.hold(calculation.hold(), false); }
            else {
                s.clearLines(); repository.flush();
                restoreAllocations(s.getId());
                long adjustment = 0;
                long available = calculation.evidence().stream().mapToLong(e -> e.result().payout()).sum();
                var receivables = adjustments.findByHostUserIdAndTestPaymentAndRemainingAmountNot(s.getHostUserId(), test, 0).stream()
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
    public Map<String, Object> command(Actor actor, Long id, String action, String key, Command command) {
        actor.require("ADMIN");
        if (key == null || key.isBlank() || key.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        String fingerprint = UUID.nameUUIDFromBytes((id + ":" + actor.id() + ":" + action + ":" + command).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var prior = audits.findByCommandKey(key);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getCommandFingerprint())) throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT");
            return detail(actor, false, id);
        }
        if ("reapprove".equals(action)) reconcileFrozen(id);
        Settlement before = owned(actor, false, id);
        if ("recalculate".equals(action)) {
            if (!before.getStatus().recalculable() || before.isManualHold()) throw new IllegalStateException("RECALCULATION_BLOCKED");
            calculateFestival(before.getFestivalId(), before.isTestPayment()); return detail(actor, false, id);
        }
        Calculation check = Set.of("confirm", "mark-paid", "reapprove").contains(action) ? gather(before.getFestivalId(), before.isTestPayment()) : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            if (!Objects.equals(before.getVersion(), s.getVersion())) throw new IllegalStateException("SETTLEMENT_VERSION_CONFLICT");
            if (check != null) {
                if (check.festival().eligibleAt().isAfter(Instant.now())) throw new IllegalStateException("SETTLEMENT_NOT_ELIGIBLE");
                if (check.hold() != null || check.evidence().size() < s.getLines().size()) throw new IllegalStateException("RECONCILIATION_REQUIRED");
                for (var current : check.evidence()) {
                    var line = s.getLines().stream().filter(l -> l.getPaymentId().equals(current.payment().getId())).findFirst();
                    var changes = adjustments.findBySourceSettlementIdAndPaymentId(id, current.payment().getId());
                    var latest = changes.stream().max(Comparator.comparing(SettlementAdjustment::getId));
                    long expectedPayout = line.map(SettlementLine::getPayoutAmount).orElse(0L) + changes.stream().mapToLong(SettlementAdjustment::getAmount).sum();
                    long expectedFace = latest.map(SettlementAdjustment::getRefundedFaceAmount).orElse(line.map(SettlementLine::getRefundedFaceAmount).orElse(0L));
                    long expectedCash = latest.map(SettlementAdjustment::getCustomerRefundAmount).orElse(line.map(SettlementLine::getCustomerRefundAmount).orElse(0L));
                    var locked = payments.findLockedById(current.payment().getId()).orElseThrow();
                    if (!Objects.equals(locked.getVersion(), current.payment().getVersion())) throw new IllegalStateException("PAYMENT_CHANGED");
                    var localRefunds = cancellations.findByPayment(locked);
                    if (localRefunds.stream().anyMatch(c -> c.getStatus() == CancellationStatus.PENDING || c.getStatus() == CancellationStatus.REQUESTED)
                            || localRefunds.stream().filter(c -> c.getStatus() == CancellationStatus.SUCCEEDED)
                                .mapToLong(Cancellation::getAmount).sum() != current.result().cash()) throw new IllegalStateException("REFUND_CHANGED");
                    if (current.result().payout() != expectedPayout || current.result().face() != expectedFace
                            || current.result().cash() != expectedCash) throw new IllegalStateException("RECONCILIATION_REQUIRED");
                }
                long adjustment = adjustments.findBySourceSettlementId(id).stream().filter(a -> "PRE_PAYMENT".equals(a.getKind()))
                        .mapToLong(SettlementAdjustment::getAmount).sum();
                if ("mark-paid".equals(action) && adjustment != s.getConfirmedAdjustmentAmount()) throw new IllegalStateException("REAPPROVAL_REQUIRED");
            }
            var previous = s.getStatus();
            switch (action) {
                case "confirm" -> s.confirm();
                case "reapprove" -> reapprove(s);
                case "mark-paid" -> s.markPaid(command.paidAt(), command.paymentReference(), command.memo());
                case "hold" -> { restoreAllocations(s.getId()); s.hold("MANUAL_REVIEW", true); }
                case "release" -> s.release();
                default -> throw new IllegalArgumentException("UNKNOWN_ACTION");
            }
            audits.save(new SettlementAuditLog(s, action, previous, actor.id(), command.memo(), key, fingerprint));
        });
        if ("release".equals(action)) calculateFestival(before.getFestivalId(), before.isTestPayment());
        return detail(actor, false, id);
    }
    public void reconcileFrozen(Long id) {
        Settlement before = repository.findById(id).orElseThrow();
        if (before.getStatus().recalculable()) throw new IllegalStateException("SETTLEMENT_NOT_FROZEN");
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
    private void reapprove(Settlement s) {
        if (s.getPaidAt() != null || s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED)
            throw new IllegalStateException("REAPPROVAL_BLOCKED");
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
        if (deficit != 0) throw new IllegalStateException("NEGATIVE_PAYOUT_REVIEW_REQUIRED");
        if (released > 0) adjustments.save(new SettlementAdjustment(s, null, 0, 0, released));
        s.reapprove(Math.addExact(correction, released));
    }
}
