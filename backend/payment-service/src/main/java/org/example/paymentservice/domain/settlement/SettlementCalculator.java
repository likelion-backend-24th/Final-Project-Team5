package org.example.paymentservice.domain.settlement;

import java.math.BigInteger;
import java.util.List;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;

/** 결제별 원 단위 내림. 환입은 최초 수수료와 잔존 매출 수수료의 차액이다. */
public final class SettlementCalculator {
    private SettlementCalculator() { }
    public record Refund(long face, long cash, int quantity, boolean succeeded, boolean pending) { }
    public record Input(long gross, long unitPrice, int quantity, int refundedQuantity,
                        PaymentMethodCategory method, List<Refund> refunds, long remoteCancelled) { }
    public record Result(long gross, long face, long cash, long penalty, long netSales,
                         long initialFee, long reversal, long fee, long payout) { }
    public static long fee(long amount, int bps) {
        if (amount < 0 || bps < 0 || bps > 10000) throw new IllegalArgumentException("INVALID_AMOUNT");
        return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(bps))
                .divide(BigInteger.valueOf(10000)).longValueExact();
    }
    public static Result calculate(Input input) {
        if (input.method() == null || input.method().rateBps() == null) throw new IllegalArgumentException("UNKNOWN_METHOD");
        if (input.quantity() < 1 || input.unitPrice() < 0 ||
                Math.multiplyExact(input.unitPrice(), input.quantity()) != input.gross())
            throw new IllegalArgumentException("RESERVATION_MISMATCH");
        long face = 0, cash = 0;
        int quantity = 0;
        for (Refund refund : input.refunds()) {
            if (refund.pending()) throw new IllegalArgumentException("CANCELLATION_PENDING");
            if (!refund.succeeded()) continue;
            if (refund.quantity() <= 0) throw new IllegalArgumentException("UNKNOWN_REFUND_QUANTITY");
            if (refund.cash() < 0 || refund.face() < refund.cash()
                    || refund.face() != Math.multiplyExact(input.unitPrice(), refund.quantity()))
                throw new IllegalArgumentException("REFUND_MISMATCH");
            face = Math.addExact(face, refund.face());
            cash = Math.addExact(cash, refund.cash());
            quantity = Math.addExact(quantity, refund.quantity());
        }
        if (quantity != input.refundedQuantity() || quantity > input.quantity()
                || cash != input.remoteCancelled()) throw new IllegalArgumentException("RECONCILIATION_MISMATCH");
        long initial = fee(input.gross(), input.method().rateBps());
        long net = input.gross() - face;
        long finalFee = fee(net, input.method().rateBps());
        return new Result(input.gross(), face, cash, face - cash, net,
                initial, initial - finalFee, finalFee, input.gross() - cash - finalFee);
    }
}
