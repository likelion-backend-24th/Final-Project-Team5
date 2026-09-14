package org.example.reservationservice.domain.refund;

/**
 * 환불 견적. Payment-Service가 PortOne에 취소를 넣기 전에 "얼마를 돌려줄 수 있는지"를 이 값으로 판단한다.
 * 환불 금액 계산은 공연 일정을 아는 Reservation-Service가 책임진다(Payment-Service는 금액만 받아 집행).
 */
public record RefundQuote(
        boolean refundable,
        /** refundable=false일 때만 채워지는 거절 사유 코드. */
        String rejectReason,
        /** 환불 대상 수량. */
        int quantity,
        /** 위약금 비율(%). */
        int feePercent,
        /** 위약금을 떼기 전 금액(단가 × 수량). */
        long grossAmount,
        /** 위약금. */
        long feeAmount,
        /** 실제 환급액(grossAmount - feeAmount). */
        long refundAmount
) {

    public static RefundQuote rejected(String rejectReason, int quantity) {
        return new RefundQuote(false, rejectReason, quantity, 0, 0L, 0L, 0L);
    }

    public static RefundQuote allowed(int quantity, int feePercent, long grossAmount) {
        //위약금은 내림으로 계산해 참가자에게 유리한 쪽(환급액이 커지는 쪽)으로 떨어지게 한다.
        long feeAmount = grossAmount * feePercent / 100;
        return new RefundQuote(true, null, quantity, feePercent, grossAmount, feeAmount, grossAmount - feeAmount);
    }
}
