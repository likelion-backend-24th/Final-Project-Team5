package org.example.reservationservice.domain.refund;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공연 시작까지 남은 시간으로 환불 가능 여부와 위약금을 판정한다.
 *
 * 판정 순서는 "막는 조건 먼저, 금액 계산은 마지막"이다 — 환불 불가 구간인데 금액만 먼저 계산해두면
 * 화면에 환급액이 잠깐 노출되는 사고가 나기 쉽다.
 */
@Component
@RequiredArgsConstructor
public class RefundPolicy {

    public static final String REJECT_ALREADY_STARTED_OR_CUTOFF = "REFUND_WINDOW_CLOSED";

    private final RefundPolicyProperties properties;

    /**
     * @param festivalStartAt 주최자가 입력한 공연 시작 시각(타임존 없는 벽시계)
     * @param now             같은 기준 타임존으로 뽑은 현재 시각 — 호출자가 app.timezone 기준으로 넘긴다
     */
    public RefundQuote quote(LocalDateTime festivalStartAt, LocalDateTime now, int quantity, int unitPrice) {
        //공연 시작 시각을 모르면 위약금 구간을 판단할 수 없다. 이 경우 막지 않고 전액 환불로 둔다
        //(참가자에게 불리하게 추정하지 않는다).
        if (festivalStartAt == null) {
            return RefundQuote.allowed(quantity, 0, (long) unitPrice * quantity);
        }

        Duration remaining = Duration.between(now, festivalStartAt);
        if (remaining.toHours() < properties.getCutoffHours()) {
            return RefundQuote.rejected(REJECT_ALREADY_STARTED_OR_CUTOFF, quantity);
        }

        return RefundQuote.allowed(quantity, feePercentFor(remaining), (long) unitPrice * quantity);
    }

    //남은 일수가 큰 구간부터 확인해, 처음 만족하는 구간의 위약금을 쓴다.
    private int feePercentFor(Duration remaining) {
        long daysBefore = remaining.toDays();
        return properties.getTiers().stream()
                .sorted(Comparator.comparingInt(RefundPolicyProperties.Tier::getDaysBefore).reversed())
                .filter(tier -> daysBefore >= tier.getDaysBefore())
                .map(RefundPolicyProperties.Tier::getFeePercent)
                .findFirst()
                //구간표에 걸리지 않을 만큼 임박한 경우(24시간 컷오프 직전). 가장 높은 위약금을 적용한다.
                .orElseGet(() -> properties.getTiers().stream()
                        .mapToInt(RefundPolicyProperties.Tier::getFeePercent)
                        .max()
                        .orElse(0));
    }
}
