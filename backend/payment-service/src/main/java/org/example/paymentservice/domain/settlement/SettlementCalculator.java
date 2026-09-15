package org.example.paymentservice.domain.settlement;

import java.math.BigInteger;
import java.util.List;

import org.example.paymentservice.domain.payment.PaymentMethodCategory;

/**
 * 결제별 원 단위 내림. 환입은 최초 수수료와 잔존 매출 수수료의 차액이다.
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    public record Refund(long face, long cash, int quantity, boolean succeeded, boolean pending) {
    }

    public record Input(
            long gross,
            long unitPrice,
            int quantity,
            int refundedQuantity,
            PaymentMethodCategory method,
            List<Refund> refunds,
            long remoteCancelled
    ) {
    }

    public record Result(
            long gross,
            long face,
            long cash,
            long penalty,
            long netSales,
            long initialFee,
            long reversal,
            long fee,
            long payout
    ) {
    }

    public static long fee(long amount, int bps) {
        if (amount < 0 || bps < 0 || bps > 10000) {
            throw new IllegalArgumentException("INVALID_AMOUNT");
        }
        return BigInteger.valueOf(amount)
                .multiply(BigInteger.valueOf(bps))
                .divide(BigInteger.valueOf(10000))
                .longValueExact();
    }

    public static Result calculate(Input input) {
        // 수수료율을 알 수 없는 결제수단에는 임의 요율을 적용하지 않는다.
        if (input.method() == null || input.method().rateBps() == null) {
            throw new IllegalArgumentException("UNKNOWN_METHOD");
        }
        // 티켓 단가와 수량의 곱이 원결제액과 달라지면 수량 기준 정산을 할 수 없다.
        if (input.quantity() < 1 || input.unitPrice() < 0
                || Math.multiplyExact(input.unitPrice(), input.quantity()) != input.gross()) {
            throw new IllegalArgumentException("RESERVATION_MISMATCH");
        }
        long face = 0,
                cash = 0;
        int quantity = 0;
        for (Refund refund : input.refunds()) {
            // 완료되지 않은 취소는 최종 환급액을 바꿀 수 있다.
            if (refund.pending()) {
                throw new IllegalArgumentException("CANCELLATION_PENDING");
            }
            if (!refund.succeeded()) {
                continue;
            }
            // 외부 취소 수량이 없으면 잔여 티켓 매출과 수수료를 계산할 수 없다.
            if (refund.quantity() <= 0) {
                throw new IllegalArgumentException("UNKNOWN_REFUND_QUANTITY");
            }
            // 환불 액면가와 실제 환급액이 유효해야 위약금을 분리할 수 있다.
            if (refund.cash() < 0 || refund.face() < refund.cash() || refund.face() != Math.multiplyExact(
                    input.unitPrice(), refund.quantity())) {
                throw new IllegalArgumentException("REFUND_MISMATCH");
            }
            face = Math.addExact(face, refund.face());
            cash = Math.addExact(cash, refund.cash());
            quantity = Math.addExact(quantity, refund.quantity());
        }
        // PG 누적 환급액과 예약 환불 수량을 모두 대조해야 전량 환불을 판별할 수 있다.
        if (quantity != input.refundedQuantity() || quantity > input.quantity() || cash != input.remoteCancelled()) {
            throw new IllegalArgumentException("RECONCILIATION_MISMATCH");
        }
        long initial = fee(input.gross(), input.method().rateBps());
        long net = input.gross() - face;
        long finalFee = fee(net, input.method().rateBps());
        return new Result(
                input.gross(),
                face,
                cash,
                face - cash,
                net,
                initial,
                initial - finalFee,
                finalFee,
                input.gross() - cash - finalFee
        );
    }
}
