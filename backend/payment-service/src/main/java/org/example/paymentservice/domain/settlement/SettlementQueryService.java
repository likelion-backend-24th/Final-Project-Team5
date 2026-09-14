package org.example.paymentservice.domain.settlement;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.settlement.dto.SettlementActor;
import org.example.paymentservice.domain.settlement.dto.SettlementAdjustmentResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementDetailResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementFilter;
import org.example.paymentservice.domain.settlement.dto.SettlementLineResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementRepository repository;
    private final SettlementAdjustmentRepository adjustments;
    private final SettlementAuditLogRepository audits;
    private final PlatformTransactionManager transactionManager;

    public Page<SettlementResponse> list(
        SettlementActor actor,
        boolean host,
        SettlementFilter filter,
        int page,
        int size
    ) {
        actor.require(host ? "HOST" : "ADMIN");
        if (
            page < 0 ||
            size < 1 ||
            size > 100 ||
            (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to()))
        ) throw new ApiException(SettlementErrorCode.INVALID_FILTER);
        return new TransactionTemplate(transactionManager).execute(tx ->
            repository
                .findAll(spec(actor, host, filter), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(this::publicView)
        );
    }

    private Specification<Settlement> spec(SettlementActor actor, boolean host, SettlementFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isNotNull(root.get("activeFestivalId")));
            if (f.festivalName() != null && !f.festivalName().isBlank()) predicates.add(
                cb.like(cb.lower(root.get("festivalName")), contains(f.festivalName()), '!')
            );
            if (!host && f.hostName() != null && !f.hostName().isBlank()) predicates.add(
                cb.like(cb.lower(root.get("hostName")), contains(f.hostName()), '!')
            );
            Long owner = host ? actor.id() : f.hostUserId();
            if (owner != null) predicates.add(cb.equal(root.get("hostUserId"), owner));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.festivalId() != null) predicates.add(cb.equal(root.get("festivalId"), f.festivalId()));
            if ("PAID_AT".equals(f.dateBasis()) && (f.from() != null || f.to() != null)) {
                var sub = query.subquery(Long.class);
                var line = sub.from(SettlementLine.class);
                var dates = new ArrayList<Predicate>();
                dates.add(cb.equal(line.get("settlement"), root));
                if (f.from() != null) dates.add(cb.greaterThanOrEqualTo(line.get("paidAt"), f.from()));
                if (f.to() != null) dates.add(cb.lessThan(line.get("paidAt"), f.to()));
                sub.select(line.get("id")).where(dates.toArray(Predicate[]::new));
                predicates.add(cb.exists(sub));
            } else {
                if (f.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedAt"), f.from()));
                if (f.to() != null) predicates.add(cb.lessThan(root.get("calculatedAt"), f.to()));
            }
            if (f.paymentMethod() != null) {
                var sub = query.subquery(Long.class);
                var line = sub.from(SettlementLine.class);
                sub.select(line.get("id")).where(
                    cb.equal(line.get("settlement"), root),
                    cb.equal(line.get("paymentMethod"), f.paymentMethod())
                );
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public SettlementSummaryResponse summary(SettlementActor actor, boolean host, SettlementFilter filter) {
        actor.require(host ? "HOST" : "ADMIN");
        return new TransactionTemplate(transactionManager).execute(tx -> {
            var rows = repository.findAll(spec(actor, host, filter));
            return new SettlementSummaryResponse(
                "KRW",
                filter.dateBasis(),
                rows.size(),
                rows.stream().mapToLong(Settlement::getGrossPaymentAmount).sum(),
                rows.stream().mapToLong(Settlement::getCustomerRefundAmount).sum(),
                rows.stream().mapToLong(Settlement::getPlatformFeeAmount).sum(),
                rows
                    .stream()
                    .mapToLong(s -> Math.addExact(s.getPayoutAmount(), s.getConfirmedAdjustmentAmount()))
                    .sum(),
                rows
                    .stream()
                    .filter(s -> s.getPaidAt() != null)
                    .mapToLong(s -> s.getPaidPayoutAmount() == null ? s.getPayoutAmount() : s.getPaidPayoutAmount())
                    .sum(),
                rows
                    .stream()
                    .filter(s -> s.getStatus() == SettlementStatus.HELD)
                    .count(),
                rows
                    .stream()
                    .filter(
                        s -> s.getStatus() == SettlementStatus.CALCULATED || s.getStatus() == SettlementStatus.CONFIRMED
                    )
                    .mapToLong(s -> Math.addExact(s.getPayoutAmount(), s.getConfirmedAdjustmentAmount()))
                    .sum(),
                rows
                    .stream()
                    .filter(
                        s ->
                            s.getStatus() == SettlementStatus.CALCULATED ||
                            s.getStatus() == SettlementStatus.HELD ||
                            s.getStatus() == SettlementStatus.ADJUSTMENT_REQUIRED
                    )
                    .count()
            );
        });
    }

    public SettlementDetailResponse detail(SettlementActor actor, boolean host, Long id) {
        actor.require(host ? "HOST" : "ADMIN");
        return new TransactionTemplate(transactionManager).execute(tx -> {
            Settlement s = owned(actor, host, id);
            var view = publicView(s);
            var changes = adjustments.findBySourceSettlementId(id);
            var adjustmentViews = changes
                .stream()
                .map(a ->
                    new SettlementAdjustmentResponse(
                        a.getAmount(),
                        a.getRemainingAmount(),
                        a.getStatus(),
                        a.getKind().name(),
                        a.getCreatedAt()
                    )
                )
                .toList();
            long proposed = Math.addExact(
                s.getPayoutAmount(),
                changes
                    .stream()
                    .filter(a -> a.getKind() == SettlementAdjustmentKind.PRE_PAYMENT)
                    .mapToLong(SettlementAdjustment::getAmount)
                    .sum()
            );
            var lines = s
                .getLines()
                .stream()
                .map(line ->
                    new SettlementLineResponse(
                        line.getPaymentMethod(),
                        line.getPaidAt(),
                        line.getGrossAmount(),
                        line.getRefundedFaceAmount(),
                        line.getCustomerRefundAmount(),
                        line.getPenaltyAmount(),
                        line.getFeeRateBps(),
                        line.getInitialFeeAmount(),
                        line.getFeeReversalAmount(),
                        line.getFinalFeeAmount(),
                        line.getPayoutAmount(),
                        host ? null : line.getPaymentId(),
                        host ? null : line.getReservationId()
                    )
                )
                .toList();
            return new SettlementDetailResponse(
                view.id(),
                view.version(),
                view.festivalId(),
                view.festivalName(),
                view.hostUserId(),
                view.currency(),
                view.status(),
                view.hostName(),
                view.manualHold(),
                view.eligibleAt(),
                view.calculatedAt(),
                view.confirmedAt(),
                view.paidAt(),
                view.grossPaymentAmount(),
                view.grossRefundedFaceAmount(),
                view.customerRefundAmount(),
                view.cancellationPenaltyAmount(),
                view.netTicketSalesAmount(),
                view.platformFeeAmount(),
                view.adjustmentAmount(),
                view.payoutAmount(),
                view.confirmedAdjustmentAmount(),
                view.payableAmount(),
                view.paidPayoutAmount(),
                view.reapprovedAt(),
                view.holdMessage(),
                adjustmentViews,
                proposed,
                lines,
                host ? null : audits.findBySettlementIdOrderByIdAsc(id),
                host ? null : s.getAdminMemo(),
                host ? null : s.getPaymentReference(),
                host ? null : s.getHoldReason()
            );
        });
    }

    Settlement owned(SettlementActor actor, boolean host, Long id) {
        Settlement s = repository
            .findById(id)
            .orElseThrow(() -> new ApiException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (host && !actor.id().equals(s.getHostUserId())) throw new ApiException(
            SettlementErrorCode.SETTLEMENT_NOT_FOUND
        );
        if (s.isRetired() || s.getActiveFestivalId() == null) throw new ApiException(
            SettlementErrorCode.SETTLEMENT_NOT_FOUND
        );
        return s;
    }

    private static String contains(String value) {
        return (
            "%" + value.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
        );
    }

    private SettlementResponse publicView(Settlement s) {
        return new SettlementResponse(
            s.getId(),
            s.getVersion(),
            s.getFestivalId(),
            s.getFestivalName(),
            s.getHostUserId(),
            "KRW",
            s.getStatus(),
            s.getHostName(),
            s.isManualHold(),
            s.getEligibleAt(),
            s.getCalculatedAt(),
            s.getConfirmedAt(),
            s.getPaidAt(),
            s.getGrossPaymentAmount(),
            s.getGrossRefundedFaceAmount(),
            s.getCustomerRefundAmount(),
            s.getCancellationPenaltyAmount(),
            s.getNetTicketSalesAmount(),
            s.getPlatformFeeAmount(),
            s.getAdjustmentAmount(),
            s.getPayoutAmount(),
            s.getConfirmedAdjustmentAmount(),
            Math.addExact(s.getPayoutAmount(), s.getConfirmedAdjustmentAmount()),
            s.getPaidPayoutAmount(),
            s.getReapprovedAt(),
            s.getStatus() == SettlementStatus.HELD ? "결제·환불 내역을 확인 중입니다." : null
        );
    }
}
