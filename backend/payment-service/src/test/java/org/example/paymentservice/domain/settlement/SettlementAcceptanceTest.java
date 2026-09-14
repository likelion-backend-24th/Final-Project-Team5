package org.example.paymentservice.domain.settlement;

import org.example.paymentservice.domain.payment.*;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.settlement.dto.SettlementActor;
import org.example.paymentservice.domain.settlement.dto.SettlementCommandRequest;
import org.example.paymentservice.infrastructure.portone.*;
import org.example.paymentservice.infrastructure.portone.dto.*;
import org.example.paymentservice.infrastructure.reservation.*;
import org.example.paymentservice.infrastructure.reservation.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SettlementAcceptanceTest {
    @Autowired SettlementService service;
    @Autowired SettlementQueryService queries;
    @Autowired SettlementRepository repository;
    @Autowired SettlementAdjustmentRepository adjustments;
    @Autowired SettlementAdjustmentAllocationRepository allocations;
    @Autowired SettlementAuditLogRepository audits;
    @Autowired PaymentRepository payments;
    @Autowired PaymentTransactionRepository transactions;
    @Autowired org.example.paymentservice.domain.cancellation.CancellationRepository cancellations;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean FestivalSettlementClient festivals;
    @MockitoBean ReservationServiceClient reservations;
    @MockitoBean PortOnePaymentClient portone;
    @MockitoBean SettlementHostClient hosts;
    @Autowired SettlementLedgerInitializer ledgerInitializer;
    private final SettlementActor admin = new SettlementActor(1L, "ADMIN");
    private final Instant approved = Instant.parse("2026-01-01T00:00:00Z");
    @BeforeEach void setup() {
        allocations.deleteAll(); adjustments.deleteAll(); audits.deleteAll(); repository.deleteAll();
        cancellations.deleteAll(); transactions.deleteAll(); payments.deleteAll();
        when(festivals.refundCandidates()).thenReturn(List.of());
        when(hosts.names(any())).thenReturn(Map.of(10L, "정산주최자"));
    }
    private Long calculate() {
        Payment p = payments.save(Payment.builder().paymentId("settlement-" + UUID.randomUUID()).reservationId(999L)
                .userId(30L).ticketAmount(100000).platformFee(0).currency("KRW").status(PaymentStatus.PAID).build());
        when(festivals.context(42L)).thenReturn(new FestivalSettlementClient.Context(42L, 10L, "행사", approved, "CLOSED"));
        when(reservations.settlementContext(42L)).thenReturn(List.of(new ReservationForPaymentResponse(999L, 30L, "CONFIRMED",
                100000, 1L, 2, null, 42L, 10L, 50000L, 0, p.getPaymentId())));
        when(portone.getPayment(anyString())).thenReturn(new PortOnePaymentResponse(p.getPaymentId(), "PAID", "tx", "store-test",
                new PortOnePaymentResponse.Channel("id", "channel-key-test", "LIVE", "live", "provider"),
                new PortOnePaymentResponse.Method("CARD", null, null, null, null, null, null),
                new PortOnePaymentResponse.Amount(100000, 0, 0, 0, 0, 100000, 0, 0), "KRW", "order",
                approved, approved, approved, approved, null, null, "tx", List.of()));
        service.calculateFestival(42L, false); return repository.findByFestivalIdAndTestPayment(42L, false).orElseThrow().getId();
    }
    @Test void hostOwnershipPaginationAndSensitiveFields() throws Exception {
        var s = repository.save(new Settlement(42L, 10L, "행사", approved, false));
        mvc.perform(get("/api/host/settlements/" + s.getId()).header("X-User-Id", 20).header("X-User-Role", "HOST"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/host/settlements/" + s.getId()).header("X-User-Id", 10).header("X-User-Role", "HOST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.adminMemo").doesNotExist()).andExpect(jsonPath("$.data.auditLogs").doesNotExist());
        mvc.perform(get("/api/host/settlements?hostUserId=10").header("X-User-Id", 20).header("X-User-Role", "HOST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.meta.pagination.totalItems").value(0));
        mvc.perform(get("/api/admin/settlements").header("X-User-Id", 10).header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/host/settlements/" + s.getId() + "/confirm").header("X-User-Id", 10).header("X-User-Role", "HOST")
                .header("Idempotency-Key", "test").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test void repeatedCalculationAndConfirmationAreIdempotent() {
        Long id = calculate(); service.calculateFestival(42L, false);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(queries.detail(admin, false, id).payoutAmount()).isEqualTo(92500L);
        service.command(admin, id, "confirm", "confirm-1", new SettlementCommandRequest(null, null, "check"));
        service.command(admin, id, "confirm", "confirm-1", new SettlementCommandRequest(null, null, "check"));
        assertThat(audits.findByCommandKey("confirm-1")).isPresent();
        assertThatThrownBy(() -> service.command(admin, id, "recalculate", "again", new SettlementCommandRequest(null, null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", SettlementErrorCode.RECALCULATION_BLOCKED);
    }
    @Test void optimisticLockRejectsConcurrentConfirm() throws Exception {
        var s = new Settlement(50L, 10L, "concurrent", approved, false); s.calculate(List.of(), 0); repository.save(s);
        var barrier = new CyclicBarrier(2); var successes = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                try {
                    new TransactionTemplate(manager).executeWithoutResult(tx -> {
                        var loaded = repository.findById(s.getId()).orElseThrow();
                        try { barrier.await(5, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
                        loaded.confirm(); repository.flush();
                    }); successes.incrementAndGet();
                } catch (org.springframework.dao.OptimisticLockingFailureException expected) { }
            }));
            for (var f : futures) f.get(10, TimeUnit.SECONDS);
        }
        assertThat(successes.get()).isEqualTo(1);
    }

    @Test void paidRefundCreatesOneReceivableAndPreservesSnapshot() {
        Long id = calculate();
        service.command(admin, id, "confirm", "confirm-paid", new SettlementCommandRequest(null, null, null));
        service.command(admin, id, "mark-paid", "pay-1", new SettlementCommandRequest(approved.plusSeconds(1), "BANK-1", "송금 확인"));
        var p = payments.findAll().getFirst();
        var remote = portone.getPayment(p.getPaymentId());
        var cancellation = new PortOnePaymentResponse.Cancellation("cancel-paid", "SUCCEEDED", "귀책 환불", 100000, approved, approved);
        cancellations.save(org.example.paymentservice.domain.cancellation.Cancellation.builder().payment(p)
                .cancellationId("cancel-paid").status(org.example.paymentservice.domain.cancellation.CancellationStatus.SUCCEEDED)
                .source(org.example.paymentservice.domain.cancellation.CancellationSource.API_REQUEST)
                .quantity(2).amount(100000).grossAmount(100000L).build());
        when(portone.getPayment(p.getPaymentId())).thenReturn(new PortOnePaymentResponse(p.getPaymentId(), "CANCELLED", "tx", remote.storeId(),
                remote.channel(), remote.method(), new PortOnePaymentResponse.Amount(100000, 0, 0, 0, 0, 0, 100000, 0),
                "KRW", "order", approved, approved, approved, approved, null, null, "tx", List.of(cancellation)));
        when(reservations.settlementContext(42L)).thenReturn(List.of(new ReservationForPaymentResponse(999L, 30L, "REFUNDED",
                100000, 1L, 2, null, 42L, 10L, 50000L, 2, p.getPaymentId())));
        service.reconcileFrozen(id); service.reconcileFrozen(id);
        assertThat(adjustments.count()).isEqualTo(1);
        assertThat(adjustments.findAll().getFirst().getAmount()).isEqualTo(-92500);
        assertThat(adjustments.findAll().getFirst().getStatus()).isEqualTo("RECEIVABLE");
        assertThat(repository.findById(id).orElseThrow().getPayoutAmount()).isEqualTo(92500);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(SettlementStatus.ADJUSTMENT_REQUIRED);
        var next = payments.save(Payment.builder().paymentId("next-payment").reservationId(1000L).userId(30L)
                .ticketAmount(20000).currency("KRW").status(PaymentStatus.PAID).build());
        when(festivals.context(43L)).thenReturn(new FestivalSettlementClient.Context(43L, 10L, "다음 행사", approved, "CLOSED"));
        when(reservations.settlementContext(43L)).thenReturn(List.of(new ReservationForPaymentResponse(1000L, 30L, "CONFIRMED",
                20000, 2L, 1, null, 43L, 10L, 20000L, 0, next.getPaymentId())));
        when(portone.getPayment(next.getPaymentId())).thenReturn(new PortOnePaymentResponse(next.getPaymentId(), "PAID", "next-tx", remote.storeId(),
                remote.channel(), remote.method(), new PortOnePaymentResponse.Amount(20000, 0, 0, 0, 0, 20000, 0, 0),
                "KRW", "order", approved, approved, approved, approved, null, null, "tx", List.of()));
        service.calculateFestival(43L, true); service.calculateFestival(43L, false);
        var target = repository.findByActiveFestivalId(43L).orElseThrow();
        assertThat(target.getPayoutAmount()).isZero();
        assertThat(target.getAdjustmentAmount()).isEqualTo(-18500);
        assertThat(adjustments.findAll().getFirst().getRemainingAmount()).isEqualTo(-74000);
        assertThat(allocations.count()).isEqualTo(1);
        service.command(admin, target.getId(), "confirm", "next-confirm", new SettlementCommandRequest(null, null, null));
        var nextCancel = new PortOnePaymentResponse.Cancellation("next-cancel", "SUCCEEDED", "취소", 20000, approved, approved);
        cancellations.save(org.example.paymentservice.domain.cancellation.Cancellation.builder().payment(next)
                .cancellationId(nextCancel.id()).status(org.example.paymentservice.domain.cancellation.CancellationStatus.SUCCEEDED)
                .source(org.example.paymentservice.domain.cancellation.CancellationSource.API_REQUEST)
                .quantity(1).amount(20000).grossAmount(20000L).build());
        when(portone.getPayment(next.getPaymentId())).thenReturn(new PortOnePaymentResponse(next.getPaymentId(), "CANCELLED", "next-tx", remote.storeId(),
                remote.channel(), remote.method(), new PortOnePaymentResponse.Amount(20000, 0, 0, 0, 0, 0, 20000, 0),
                "KRW", "order", approved, approved, approved, approved, null, null, "tx", List.of(nextCancel)));
        when(reservations.settlementContext(43L)).thenReturn(List.of(new ReservationForPaymentResponse(1000L, 30L, "REFUNDED",
                20000, 2L, 1, null, 43L, 10L, 20000L, 1, next.getPaymentId())));
        service.command(admin, target.getId(), "reapprove", "next-reapprove", new SettlementCommandRequest(null, null, null));
        assertThat(adjustments.findBySourceSettlementId(id).getFirst().getRemainingAmount()).isEqualTo(-92500);
        assertThat(repository.findById(target.getId()).orElseThrow().getConfirmedAdjustmentAmount()).isZero();
    }
    @Test void prePaymentRefundRequiresReapprovalAndPreservesOriginalSnapshot() {
        Long id = calculate();
        service.command(admin, id, "confirm", "first-confirm", new SettlementCommandRequest(null, null, null));
        var p = payments.findAll().getFirst(); var remote = portone.getPayment(p.getPaymentId());
        var cancel = new PortOnePaymentResponse.Cancellation("before-paid", "SUCCEEDED", "행사 취소", 50000, approved, approved);
        cancellations.save(org.example.paymentservice.domain.cancellation.Cancellation.builder().payment(p)
                .cancellationId(cancel.id()).status(org.example.paymentservice.domain.cancellation.CancellationStatus.SUCCEEDED)
                .source(org.example.paymentservice.domain.cancellation.CancellationSource.API_REQUEST)
                .quantity(1).amount(50000).grossAmount(50000L).build());
        when(portone.getPayment(p.getPaymentId())).thenReturn(new PortOnePaymentResponse(p.getPaymentId(), "PARTIAL_CANCELLED", "tx", remote.storeId(),
                remote.channel(), remote.method(), new PortOnePaymentResponse.Amount(100000, 0, 0, 0, 0, 50000, 50000, 0),
                "KRW", "order", approved, approved, approved, approved, null, null, "tx", List.of(cancel)));
        when(reservations.settlementContext(42L)).thenReturn(List.of(new ReservationForPaymentResponse(999L, 30L, "PARTIALLY_REFUNDED",
                100000, 1L, 2, null, 42L, 10L, 50000L, 1, p.getPaymentId())));
        service.reconcileFrozen(id);
        assertThatThrownBy(() -> service.command(admin, id, "mark-paid", "too-early", new SettlementCommandRequest(approved, "BANK-2", null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", SettlementErrorCode.REAPPROVAL_REQUIRED);
        service.command(admin, id, "reapprove", "reapprove-1", new SettlementCommandRequest(null, null, "차액 확인"));
        service.command(admin, id, "reapprove", "reapprove-1", new SettlementCommandRequest(null, null, "차액 확인"));
        service.command(admin, id, "mark-paid", "paid-corrected", new SettlementCommandRequest(approved, "BANK-2", null));
        var result = repository.findById(id).orElseThrow();
        assertThat(result.getPayoutAmount()).isEqualTo(92500);
        assertThat(result.getConfirmedAdjustmentAmount()).isEqualTo(-46250);
        assertThat(result.getPaidPayoutAmount()).isEqualTo(46250);
        assertThat(adjustments.findAll().getFirst().getRemainingAmount()).isZero();
        assertThatThrownBy(() -> service.command(admin, id, "reapprove", "after-paid", new SettlementCommandRequest(null, null, null)))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", SettlementErrorCode.REAPPROVAL_BLOCKED);
    }
    @Test void unknownMethodHoldsAndExternalFailureDoesNotConfirm() {
        Long id = calculate(); var p = payments.findAll().getFirst(); var remote = portone.getPayment(p.getPaymentId());
        when(portone.getPayment(p.getPaymentId())).thenReturn(new PortOnePaymentResponse(p.getPaymentId(), "PAID", "tx", remote.storeId(),
                remote.channel(), new PortOnePaymentResponse.Method("CRYPTO", null, null, null, null, null, null), remote.amount(),
                "KRW", "order", approved, approved, approved, approved, null, null, "tx", List.of()));
        service.calculateFestival(42L, false);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(SettlementStatus.HELD);
        when(portone.getPayment(p.getPaymentId())).thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
        assertThatThrownBy(() -> service.command(admin, id, "confirm", "offline", new SettlementCommandRequest(null, null, null)))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(SettlementStatus.HELD);
    }
    @Test void filterAndTestChannelAreEnforced() throws Exception {
        calculate();
        mvc.perform(get("/api/admin/settlements?status=CALCULATED&festivalId=42&paymentMethod=CARD&size=1")
                        .header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].festivalId").value(42))
                .andExpect(jsonPath("$.meta.pagination.size").value(1)).andExpect(jsonPath("$.meta.pagination.totalItems").value(1));
        mvc.perform(get("/api/admin/settlements?paymentMethod=VIRTUAL_ACCOUNT").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/admin/settlements?testPayment=true").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test void testChannelIncludedByDefaultAndNameFiltersApplyBeforePagination() throws Exception {
        Long id = calculate(); var p = payments.findAll().getFirst(); var remote = portone.getPayment(p.getPaymentId());
        when(portone.getPayment(p.getPaymentId())).thenReturn(new PortOnePaymentResponse(remote.id(), remote.status(), remote.transactionId(), remote.storeId(),
                new PortOnePaymentResponse.Channel("test", "channel-key-test", "TEST", "test", "provider"), remote.method(), remote.amount(),
                remote.currency(), remote.orderName(), approved, approved, approved, approved, null, null, "tx", List.of()));
        service.calculateFestival(42L, false);
        mvc.perform(get("/api/admin/settlements?festivalName=행&hostName=주최&size=1").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].payoutAmount").value(92500))
                .andExpect(jsonPath("$.data[0].hostName").value("정산주최자")).andExpect(jsonPath("$.meta.pagination.totalItems").value(1));
        mvc.perform(get("/api/admin/settlements?hostName=없는이름").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/admin/settlements?festivalName=%25").header("X-User-Id", 1).header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/host/settlements?hostName=정산주최자").header("X-User-Id", 20).header("X-User-Role", "HOST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        assertThat(repository.findById(id).orElseThrow().getPayoutAmount()).isEqualTo(92500);
    }

    @Test void legacyEmptyDuplicateIsRetiredWithoutChangingFrozenLedger() {
        var keep = new Settlement(88L, 10L, "기존 정산", approved, true); keep.calculate(List.of(), 0); keep.confirm();
        org.springframework.test.util.ReflectionTestUtils.setField(keep, "activeFestivalId", null);
        repository.saveAndFlush(keep);
        var empty = new Settlement(88L, 10L, "빈 원장", approved, false);
        org.springframework.test.util.ReflectionTestUtils.setField(empty, "activeFestivalId", null);
        repository.saveAndFlush(empty);
        ledgerInitializer.initialize(); ledgerInitializer.initialize();
        assertThat(repository.findByActiveFestivalId(88L).orElseThrow().getId()).isEqualTo(keep.getId());
        assertThat(repository.findById(keep.getId()).orElseThrow().getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(repository.findById(empty.getId()).orElseThrow().isRetired()).isTrue();
        assertThatThrownBy(() -> queries.detail(admin, false, empty.getId())).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test void twoFrozenLegacyLedgersRequireReviewWithoutSilentMerge() {
        for (boolean test : List.of(false, true)) {
            var row = new Settlement(89L, 10L, "중복 확정", approved, test); row.calculate(List.of(), 0); row.confirm();
            org.springframework.test.util.ReflectionTestUtils.setField(row, "activeFestivalId", null);
            repository.saveAndFlush(row);
        }
        var before = repository.findByRetiredFalseOrderByIdAsc();
        assertThatCode(ledgerInitializer::initialize).doesNotThrowAnyException();
        var after = repository.findByRetiredFalseOrderByIdAsc();
        assertThat(after).hasSize(2).allSatisfy(row -> assertThat(row.getActiveFestivalId()).isNull());
        assertThat(after).extracting(Settlement::getPayoutAmount, Settlement::getConfirmedAt, Settlement::getPaidAt)
                .containsExactlyElementsOf(before.stream().map(row -> org.assertj.core.groups.Tuple.tuple(
                        row.getPayoutAmount(), row.getConfirmedAt(), row.getPaidAt())).toList());
    }
}
