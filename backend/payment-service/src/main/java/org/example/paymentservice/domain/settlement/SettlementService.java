package org.example.paymentservice.domain.settlement;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
import org.springframework.web.client.HttpClientErrorException;

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

    //정산에 포함할 수 있는 페스티벌·예매·결제·PG 상태. 취소 승인 대기(CANCELLATION_PENDING)는 환불이 끝날 때까지 제외한다.
    private static final Set<String> SETTLEABLE_FESTIVAL_STATUSES = Set.of("PUBLISHED", "CLOSED", "CANCELLED");
    private static final Set<String> PAID_RESERVATION_STATUSES = Set.of("CONFIRMED", "PARTIALLY_REFUNDED", "REFUNDED");
    private static final Set<PaymentStatus> APPROVED_PAYMENT_STATUSES =
            Set.of(PaymentStatus.PAID, PaymentStatus.PARTIAL_CANCELLED, PaymentStatus.CANCELLED);
    private static final Set<String> APPROVED_REMOTE_STATUSES = Set.of("PAID", "PARTIAL_CANCELLED", "CANCELLED");
    private static final Set<String> KNOWN_CHANNEL_TYPES = Set.of("TEST", "LIVE");
    //돈이 움직이는 처리(확정·지급·재승인) 전에는 결제·환불 근거를 다시 모아 저장된 라인과 대조한다.
    private static final EnumSet<SettlementAction> MONEY_MOVING_ACTIONS =
            EnumSet.of(SettlementAction.CONFIRM, SettlementAction.MARK_PAID, SettlementAction.REAPPROVE);

    private record Evidence(
            Payment payment,
            ReservationForPaymentResponse reservation,
            PaymentMethodCategory method,
            Instant paidAt,
            SettlementCalculator.Result result
    ) {
    }

    private record Calculation(FestivalSettlementClient.Context festival, List<Evidence> evidence, String hold) {
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshHostNames() {
        var missing = repository.findByRetiredFalseOrderByIdAsc()
                .stream()
                .filter(s -> s.getHostName() == null)
                .toList();
        var names = hosts.names(missing.stream().map(Settlement::getHostUserId).toList());
        if (names.isEmpty()) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            for (var s : missing) {
                repository.findById(s.getId()).orElseThrow().snapshotHostName(names.get(s.getHostUserId()));
            }
        });
    }

    //정산 근거 수집. PG(PortOne)·예매·결제 세 곳의 기록이 서로 맞을 때만 라인을 만들고, 하나라도 어긋나면
    //금액을 추정하지 않고 보류 사유(hold)를 돌려준다 — 잘못 지급하는 것보다 사람이 확인하는 편이 싸다.
    private Calculation gather(Long festivalId, boolean test) {
        var context = festivals.context(festivalId);
        //소유자나 정산 가능 시각이 없으면 지급 대상과 유예기간을 확정할 수 없다.
        if (context.hostUserId() == null || context.eligibleAt() == null) {
            return new Calculation(context, List.of(), "MISSING_FESTIVAL_CONTEXT");
        }
        //행사 취소 환불이 끝나기 전에는 지급 가능한 매출을 확정하지 않는다.
        if (!SETTLEABLE_FESTIVAL_STATUSES.contains(context.status())) {
            return new Calculation(context, List.of(), "ORGANIZER_REFUND_PENDING");
        }
        var reservationRows = reservations.settlementContext(festivalId);
        var byId = new HashMap<Long, ReservationForPaymentResponse>();
        reservationRows.forEach(r -> byId.put(r.reservationId(), r));
        var paymentRows = payments.findByReservationIdIn(byId.keySet());
        var evidence = new ArrayList<Evidence>();
        try {
            for (var reservation : reservationRows) {
                //유효한 예약에 결제 근거가 없으면 누락된 매출을 추정하지 않는다.
                if (PAID_RESERVATION_STATUSES.contains(reservation.status())
                        && paymentRows.stream().noneMatch(p -> p.getPaymentId().equals(reservation.paymentId()))) {
                    throw new IllegalArgumentException("MISSING_PAYMENT");
                }
            }
            for (Payment payment : paymentRows) {
                if (!APPROVED_PAYMENT_STATUSES.contains(payment.getStatus())) {
                    continue;
                }
                //예매 확정이 거절돼 자동 전액 환불(보상)되는 결제는 티켓을 준 매출이 아니다 — 환불이 끝났으면 빼고, 아직이면 보류한다.
                if (payment.isReservationRejected()) {
                    if (payment.getStatus() != PaymentStatus.CANCELLED) {
                        throw new IllegalArgumentException("COMPENSATION_REFUND_PENDING");
                    }
                    continue;
                }
                var reservation = byId.get(payment.getReservationId());
                var remote = portone.getPayment(payment.getPaymentId());
                //채널 출처가 확인되지 않은 결제를 승인 거래로 가정하지 않는다.
                if (remote == null || remote.channel() == null
                        || !KNOWN_CHANNEL_TYPES.contains(remote.channel().type())) {
                    throw new IllegalArgumentException("UNKNOWN_TEST_CHANNEL");
                }
                //채널(TEST/LIVE) 구분 없이 승인된 결제를 같은 정산에 포함한다.
                //PG와 예약의 식별자·통화·금액·수량 근거가 모두 일치해야 중복 집계나 오지급을 막을 수 있다.
                boolean remoteMatchesPayment = remote.amount() != null
                        && remote.amount().total() == payment.getTicketAmount()
                        && "KRW".equals(remote.currency())
                        && payment.getPaymentId().equals(remote.id())
                        && storeId.equals(remote.storeId())
                        && APPROVED_REMOTE_STATUSES.contains(remote.status())
                        && remote.paidAt() != null;
                boolean reservationMatchesPayment = reservation.refundedQuantity() != null
                        && reservation.unitPrice() != null
                        && payment.getPaymentId().equals(reservation.paymentId())
                        && reservation.totalAmount() == payment.getTicketAmount();
                if (!remoteMatchesPayment || !reservationMatchesPayment) {
                    throw new IllegalArgumentException("PAYMENT_RESERVATION_MISMATCH");
                }
                var method = PaymentMethodCategory.fromRaw(remote.method() == null ? null : remote.method().type());
                var refunds = new ArrayList<SettlementCalculator.Refund>();
                var local = cancellations.findByPayment(payment);
                for (var c : local) {
                    //실제 환급액만으로 환불 티켓 액면가와 수수료 환입을 역산하지 않는다 — 취소 시점 스냅샷이 있어야 한다.
                    if (c.getStatus() == CancellationStatus.SUCCEEDED && c.getGrossAmount() == null) {
                        throw new IllegalArgumentException("MISSING_REFUND_SNAPSHOT");
                    }
                    boolean inProgress = c.getStatus() == CancellationStatus.REQUESTED
                            || c.getStatus() == CancellationStatus.PENDING;
                    refunds.add(new SettlementCalculator.Refund(
                            c.getGrossAmount() == null ? 0 : c.getGrossAmount(),
                            c.getAmount(),
                            c.getQuantity(),
                            c.getStatus() == CancellationStatus.SUCCEEDED,
                            inProgress));
                }
                if (remote.cancellations() != null) {
                    for (var c : remote.cancellations()) {
                        //완료되지 않은 취소는 최종 환급액을 바꿀 수 있다.
                        if (Set.of("REQUESTED", "PENDING").contains(c.status())) {
                            throw new IllegalArgumentException("CANCELLATION_PENDING");
                        }
                        //PG에는 성공한 취소가 있는데 우리 쪽에 같은 ID·금액의 성공 기록이 없으면(대시보드 취소 등)
                        //환불 수량을 알 수 없어 확정할 수 없다.
                        boolean knownLocally = local.stream().anyMatch(l ->
                                Objects.equals(l.getCancellationId(), c.id())
                                        && l.getStatus() == CancellationStatus.SUCCEEDED
                                        && l.getAmount() == c.totalAmount());
                        if ("SUCCEEDED".equals(c.status()) && !knownLocally) {
                            throw new IllegalArgumentException("UNKNOWN_EXTERNAL_CANCELLATION");
                        }
                    }
                }
                var result = SettlementCalculator.calculate(new SettlementCalculator.Input(
                        payment.getTicketAmount(),
                        reservation.unitPrice(),
                        reservation.quantity(),
                        reservation.refundedQuantity(),
                        method,
                        refunds,
                        remote.amount().cancelled()));
                evidence.add(new Evidence(payment, reservation, method, remote.paidAt(), result));
            }
            return new Calculation(context, evidence, null);
        } catch (HttpClientErrorException.NotFound e) {
            //PG에 결제 근거가 없으면 원장을 건너뛰지 않고 확인 가능한 보류 사유로 남긴다.
            return new Calculation(context, List.of(), "PG_PAYMENT_NOT_FOUND");
        } catch (IllegalArgumentException e) {
            //산출 중 발견한 불일치는 예외가 아니라 보류 사유로 변환한다(SettlementCalculator도 같은 방식으로 던진다).
            return new Calculation(context, List.of(), e.getMessage());
        }
    }

    public void calculateFestival(Long festivalId, boolean test) {
        // 수동 병합 대상에 새 원장을 만들면 기존 확정 금액을 중복 집계할 수 있다.
        if (repository.existsByFestivalIdAndActiveFestivalIdIsNullAndRetiredFalse(festivalId)) {
            return;
        }
        var existing = repository.findByActiveFestivalId(festivalId);
        if (existing.isPresent() && (!existing.get().getStatus().recalculable() || existing.get().isManualHold())) {
            return;
        }
        Calculation calculation = gather(festivalId, test);
        if (calculation.festival().eligibleAt() == null || calculation.festival().hostUserId() == null) {
            throw new ApiException(SettlementErrorCode.MISSING_FESTIVAL_CONTEXT);
        }
        var hostNames = hosts.names(List.of(calculation.festival().hostUserId()));
        if (calculation.festival().eligibleAt().isAfter(Instant.now())) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findByActiveFestivalId(festivalId)
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
            if (!s.getStatus().recalculable() || s.isManualHold()) {
                return;
            }
            SettlementStatus previous = s.getStatus();
            if (calculation.hold() != null) {
                restoreAllocations(s.getId());
                s.hold(calculation.hold(), false);
            } else {
                s.clearLines();
                repository.flush();
                restoreAllocations(s.getId());
                long adjustment = 0;
                long available = calculation.evidence()
                        .stream()
                        .mapToLong(e -> e.result().payout())
                        .sum();
                var receivables = adjustments.findByHostUserIdAndRemainingAmountNot(s.getHostUserId(), 0)
                        .stream()
                        .sorted(Comparator.comparingLong(SettlementAdjustment::getRemainingAmount).reversed())
                        .toList();
                // 남은 지급액까지만 미수금을 상계하고 부족분은 다음 정산을 위해 보존한다.
                for (var a : receivables) {
                    if (a.getSourceSettlementId().equals(s.getId())) {
                        continue;
                    }
                    long applied =
                            a.getRemainingAmount() > 0
                                    ? a.getRemainingAmount()
                                    : Math.max(a.getRemainingAmount(), -available);
                    if (applied == 0) {
                        continue;
                    }
                    a.allocate(applied);
                    allocations.save(new SettlementAdjustmentAllocation(a.getId(), s.getId(), applied));
                    adjustment = Math.addExact(adjustment, applied);
                    available = Math.addExact(available, applied);
                }
                var lines = calculation.evidence()
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
        SettlementAction operation = SettlementAction.fromPath(action);
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(SettlementErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        // 같은 멱등 키의 "재시도"(같은 내용)와 "재사용"(다른 내용)을 구별한다 — 후자는 거부해야 실수로 다른 처리가 되지 않는다.
        String fingerprint = UUID.nameUUIDFromBytes(
                (id + ":" + actor.id() + ":" + operation.pathValue() + ":" + command.fingerprintValue())
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
        var prior = audits.findByCommandKey(key);
        if (prior.isPresent()) {
            if (!fingerprint.equals(prior.get().getCommandFingerprint())) {
                throw new ApiException(SettlementErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return queries.detail(actor, false, id);
        }
        if (operation == SettlementAction.REAPPROVE) {
            reconcileFrozen(id);
        }
        Settlement before = queries.owned(actor, false, id);
        if (operation == SettlementAction.RECALCULATE) {
            if (!before.getStatus().recalculable() || before.isManualHold()) {
                throw new ApiException(SettlementErrorCode.RECALCULATION_BLOCKED);
            }
            calculateFestival(before.getFestivalId(), before.isTestPayment());
            return queries.detail(actor, false, id);
        }
        Calculation check = MONEY_MOVING_ACTIONS.contains(operation)
                ? gather(before.getFestivalId(), before.isTestPayment())
                : null;
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            if (!Objects.equals(before.getVersion(), s.getVersion())) {
                throw new ApiException(SettlementErrorCode.SETTLEMENT_VERSION_CONFLICT);
            }
            if (check != null) {
                if (check.festival().eligibleAt().isAfter(Instant.now())) {
                    throw new ApiException(SettlementErrorCode.SETTLEMENT_NOT_ELIGIBLE);
                }
                if (check.hold() != null || check.evidence().size() < s.getLines().size()) {
                    throw new ApiException(SettlementErrorCode.RECONCILIATION_REQUIRED);
                }
                for (var current : check.evidence()) {
                    var line = s.getLines()
                            .stream()
                            .filter(l -> l.getPaymentId().equals(current.payment().getId()))
                            .findFirst();
                    var changes = adjustments.findBySourceSettlementIdAndPaymentId(id, current.payment().getId());
                    var latest = changes.stream().max(Comparator.comparing(SettlementAdjustment::getId));
                    long expectedPayout = line.map(SettlementLine::getPayoutAmount).orElse(0L)
                            + changes.stream().mapToLong(SettlementAdjustment::getAmount).sum();
                    long expectedFace = latest.map(SettlementAdjustment::getRefundedFaceAmount)
                            .orElse(line.map(SettlementLine::getRefundedFaceAmount).orElse(0L));
                    long expectedCash = latest.map(SettlementAdjustment::getCustomerRefundAmount)
                            .orElse(line.map(SettlementLine::getCustomerRefundAmount).orElse(0L));
                    var locked = payments.findLockedById(current.payment().getId()).orElseThrow();
                    if (!Objects.equals(locked.getVersion(), current.payment().getVersion())) {
                        throw new ApiException(SettlementErrorCode.PAYMENT_CHANGED);
                    }
                    var localRefunds = cancellations.findByPayment(locked);
                    boolean refundInProgress = localRefunds.stream().anyMatch(c ->
                            c.getStatus() == CancellationStatus.PENDING || c.getStatus() == CancellationStatus.REQUESTED);
                    long refundedCash = localRefunds.stream()
                            .filter(c -> c.getStatus() == CancellationStatus.SUCCEEDED)
                            .mapToLong(Cancellation::getAmount)
                            .sum();
                    if (refundInProgress || refundedCash != current.result().cash()) {
                        throw new ApiException(SettlementErrorCode.REFUND_CHANGED);
                    }
                    if (current.result().payout() != expectedPayout || current.result().face() != expectedFace
                            || current.result().cash() != expectedCash) {
                        throw new ApiException(SettlementErrorCode.RECONCILIATION_REQUIRED);
                    }
                }
                long adjustment = adjustments.findBySourceSettlementId(id)
                        .stream()
                        .filter(a -> a.getKind() == SettlementAdjustmentKind.PRE_PAYMENT)
                        .mapToLong(SettlementAdjustment::getAmount)
                        .sum();
                if (operation == SettlementAction.MARK_PAID && adjustment != s.getConfirmedAdjustmentAmount()) {
                    throw new ApiException(SettlementErrorCode.REAPPROVAL_REQUIRED);
                }
            }
            validateCommand(s, operation, command);
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
                    new SettlementAuditLog(s, operation.pathValue(), previous, actor.id(), command.memo(), key,
                            fingerprint)
            );
        });
        if (operation == SettlementAction.RELEASE) {
            calculateFestival(before.getFestivalId(), before.isTestPayment());
        }
        return queries.detail(actor, false, id);
    }

    public void reconcileFrozen(Long id) {
        Settlement before = repository.findById(id).orElseThrow();
        // 수동 병합이 필요한 중복 원장은 자동 대사로도 변경하지 않는다.
        if (before.isRetired() || before.getActiveFestivalId() == null) {
            return;
        }
        if (before.getStatus().recalculable()) {
            throw new ApiException(SettlementErrorCode.SETTLEMENT_NOT_FROZEN);
        }
        Calculation calculation = gather(before.getFestivalId(), before.isTestPayment());
        if (calculation.hold() != null) {
            return;
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Settlement s = repository.findById(id).orElseThrow();
            for (var current : calculation.evidence()) {
                Long paymentId = current.payment().getId();
                long original = s.getLines()
                        .stream()
                        .filter(l -> l.getPaymentId().equals(paymentId))
                        .mapToLong(SettlementLine::getPayoutAmount)
                        .sum();
                long applied = adjustments.findBySourceSettlementIdAndPaymentId(id, paymentId)
                        .stream()
                        .mapToLong(SettlementAdjustment::getAmount)
                        .sum();
                // 이미 반영한 조정까지 제외한 차액만 저장해 반복 대사에서 중복 환입을 막는다.
                long delta = current.result().payout() - original - applied;
                if (delta != 0) {
                    adjustments.save(
                            new SettlementAdjustment(s, paymentId, current.result().face(), current.result().cash(),
                                    delta)
                    );
                    var previous = s.getStatus();
                    if (s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED) {
                        s.transition(
                                SettlementStatus.ADJUSTMENT_REQUIRED
                        );
                    }
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
        for (var allocation : previous) {
            adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(allocation.getAmount());
        }
        allocations.deleteAll(previous);
        allocations.flush();
    }

    // 엔티티의 최후 방어선에 도달하기 전에 운영자가 이해할 수 있는 업무 오류로 응답한다.
    private void validateCommand(Settlement s, SettlementAction operation, SettlementCommandRequest command) {
        boolean allowed = switch (operation) {
            case CONFIRM -> s.getStatus() == SettlementStatus.CALCULATED;
            case REAPPROVE -> s.getStatus() == SettlementStatus.ADJUSTMENT_REQUIRED && s.getPaidAt() == null;
            case MARK_PAID -> s.getStatus() == SettlementStatus.CONFIRMED;
            case HOLD -> s.getStatus().permits(SettlementStatus.HELD);
            case RELEASE -> s.getStatus() == SettlementStatus.HELD;
            default -> throw new ApiException(SettlementErrorCode.UNKNOWN_ACTION);
        };
        if (!allowed) {
            throw new ApiException(operation == SettlementAction.REAPPROVE
                    ? SettlementErrorCode.REAPPROVAL_BLOCKED
                    : SettlementErrorCode.SETTLEMENT_STATE_CONFLICT);
        }
        if (operation == SettlementAction.CONFIRM && s.getPayoutAmount() < 0) {
            throw new ApiException(SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED);
        }
        //지급 완료 기록은 "실제로 송금했다"는 증빙(확인 번호·시각)이 있어야 하고, 미래 시각은 받지 않는다.
        if (operation == SettlementAction.MARK_PAID) {
            boolean paidAtValid = command.paidAt() != null && !command.paidAt().isAfter(Instant.now());
            boolean referenceValid = command.paymentReference() != null && !command.paymentReference().isBlank();
            if (!paidAtValid || !referenceValid) {
                throw new ApiException(SettlementErrorCode.PAYMENT_REFERENCE_REQUIRED);
            }
        }
    }

    private void reapprove(Settlement s) {
        if (s.getPaidAt() != null || s.getStatus() != SettlementStatus.ADJUSTMENT_REQUIRED) {
            throw new ApiException(SettlementErrorCode.REAPPROVAL_BLOCKED);
        }
        long correction = adjustments.findBySourceSettlementId(s.getId())
                .stream()
                .filter(a -> a.getKind() == SettlementAdjustmentKind.PRE_PAYMENT)
                .mapToLong(SettlementAdjustment::getAmount)
                .sum();
        long deficit = Math.max(0, -Math.addExact(s.getPayoutAmount(), correction));
        long released = 0;
        // 이번 환불로 지급액이 부족하면 이전 미수금 상계를 먼저 풀어 이중 차감을 피한다.
        for (var allocation : allocations.findBySettlementId(s.getId())) {
            if (deficit == 0) {
                break;
            }
            if (allocation.getAmount() >= 0) {
                continue;
            }
            long restore = Math.min(deficit, -allocation.getAmount());
            adjustments.findById(allocation.getAdjustmentId()).orElseThrow().restore(-restore);
            allocation.releaseDebt(restore);
            released = Math.addExact(released, restore);
            deficit -= restore;
        }
        if (deficit != 0) {
            throw new ApiException(SettlementErrorCode.NEGATIVE_PAYOUT_REVIEW_REQUIRED);
        }
        if (released > 0) {
            adjustments.save(new SettlementAdjustment(s, null, 0, 0, released));
        }
        s.reapprove(Math.addExact(correction, released));
    }
}
