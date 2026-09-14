package org.example.paymentservice.domain.settlement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.example.paymentservice.domain.payment.PaymentMethodCategory.*;
import static org.example.paymentservice.domain.settlement.SettlementCalculator.*;

class SettlementCalculatorTest {
    @ParameterizedTest @EnumSource(value = PaymentMethodCategory.class, names = {"CARD", "EASY_PAY", "VIRTUAL_ACCOUNT"})
    void feePolicyAndCashInvariant(PaymentMethodCategory method) {
        var r = calculate(new Input(100000, 50000, 2, 0, method, List.of(), 0));
        assertThat(r.fee()).isEqualTo(method == VIRTUAL_ACCOUNT ? 5000 : 7500);
        assertThat(r.gross() - r.cash()).isEqualTo(r.payout() + r.fee());
    }
    @Test void partialRefundWithPenalty() {
        var r = calculate(new Input(100000, 50000, 2, 1, CARD, List.of(new Refund(50000, 40000, 1, true, false)), 40000));
        assertThat(r.payout()).isEqualTo(56250); assertThat(r.reversal()).isEqualTo(3750);
    }
    @Test void multipleRefundsAndAllTicketsRefundedWithPenalty() {
        var r = calculate(new Input(100000, 50000, 2, 2, CARD,
                List.of(new Refund(50000, 40000, 1, true, false), new Refund(50000, 40000, 1, true, false)), 80000));
        assertThat(r.netSales()).isZero(); assertThat(r.fee()).isZero(); assertThat(r.payout()).isEqualTo(20000);
        assertThat(r.gross() - r.cash()).isEqualTo(r.payout() + r.fee());
    }
    @Test void organizerFaultHasNoPenalty() {
        var r = calculate(new Input(100000, 50000, 2, 2, CARD, List.of(new Refund(100000, 100000, 2, true, false)), 100000));
        assertThat(r.payout()).isZero(); assertThat(r.penalty()).isZero(); assertThat(r.reversal()).isEqualTo(7500);
    }
    @Test void perPaymentFloorAndMixedFestival() {
        var card = calculate(new Input(13, 13, 1, 0, CARD, List.of(), 0));
        var bank = calculate(new Input(39, 39, 1, 0, VIRTUAL_ACCOUNT, List.of(), 0));
        assertThat(card.fee()).isZero(); assertThat(bank.fee()).isEqualTo(1);
        assertThat(card.payout() + bank.payout() + card.fee() + bank.fee()).isEqualTo(52);
    }
    @Test void cumulativeReversalPreservesFinalFloorAcrossRoundingBoundary() {
        var r = calculate(new Input(26, 13, 2, 1, CARD, List.of(new Refund(13, 13, 1, true, false)), 13));
        assertThat(r.initialFee()).isEqualTo(1); assertThat(r.fee()).isZero(); assertThat(r.reversal()).isEqualTo(1);
    }
    @Test void unknownMethodHeld() {
        assertThatThrownBy(() -> calculate(new Input(10, 10, 1, 0, UNKNOWN, List.of(), 0))).hasMessage("UNKNOWN_METHOD");
    }
    @Test void pendingRefundHeld() {
        assertThatThrownBy(() -> calculate(new Input(10, 10, 1, 0, CARD, List.of(new Refund(10, 10, 1, false, true)), 0)))
                .hasMessage("CANCELLATION_PENDING");
    }
    @Test void externalUnknownQuantityHeld() {
        assertThatThrownBy(() -> calculate(new Input(10, 10, 1, 0, CARD, List.of(new Refund(10, 10, 0, true, false)), 10)))
                .hasMessage("UNKNOWN_REFUND_QUANTITY");
    }
    @Test void reservationOrRemoteMismatchHeld() {
        assertThatThrownBy(() -> calculate(new Input(10, 10, 1, 0, CARD, List.of(), 5))).hasMessage("RECONCILIATION_MISMATCH");
        assertThatThrownBy(() -> calculate(new Input(10, 10, 1, 1, CARD, List.of(), 0))).hasMessage("RECONCILIATION_MISMATCH");
    }
    @Test void floorDoesNotOverflow() { assertThat(fee(Long.MAX_VALUE, 750)).isEqualTo(691752902764108185L); }
}
