package org.example.reservationservice.refund;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.example.reservationservice.domain.refund.RefundPolicy;
import org.example.reservationservice.domain.refund.RefundPolicyProperties;
import org.example.reservationservice.domain.refund.RefundQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 환불 위약금 구간 판정 테스트. 구간 경계(정확히 10일/7일/3일/24시간)는 실수가 나기 쉬워
 * 경계값 위주로 확인한다.
 */
class RefundPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 12, 0);
    private static final int UNIT_PRICE = 10_000;

    private RefundPolicy refundPolicy;

    @BeforeEach
    void setUp() {
        RefundPolicyProperties properties = new RefundPolicyProperties();
        properties.setCutoffHours(24);
        properties.setTiers(List.of(
                tier(10, 0),
                tier(7, 10),
                tier(3, 20),
                tier(1, 30)
        ));
        refundPolicy = new RefundPolicy(properties);
    }

    private static RefundPolicyProperties.Tier tier(int daysBefore, int feePercent) {
        RefundPolicyProperties.Tier tier = new RefundPolicyProperties.Tier();
        tier.setDaysBefore(daysBefore);
        tier.setFeePercent(feePercent);
        return tier;
    }

    @Test
    @DisplayName("공연 10일 전 이상이면 위약금 없이 전액 환불된다")
    void 열흘_전_이상이면_전액_환불() {
        RefundQuote quote = refundPolicy.quote(NOW.plusDays(10), NOW, 2, UNIT_PRICE);

        assertThat(quote.refundable()).isTrue();
        assertThat(quote.feePercent()).isZero();
        assertThat(quote.grossAmount()).isEqualTo(20_000);
        assertThat(quote.feeAmount()).isZero();
        assertThat(quote.refundAmount()).isEqualTo(20_000);
    }

    @Test
    @DisplayName("공연 9일 전이면 위약금 10%를 뗀다")
    void 아흐레_전은_위약금_10퍼센트() {
        RefundQuote quote = refundPolicy.quote(NOW.plusDays(9), NOW, 1, UNIT_PRICE);

        assertThat(quote.feePercent()).isEqualTo(10);
        assertThat(quote.feeAmount()).isEqualTo(1_000);
        assertThat(quote.refundAmount()).isEqualTo(9_000);
    }

    @Test
    @DisplayName("공연 3일 전이면 위약금 20%를 뗀다")
    void 사흘_전은_위약금_20퍼센트() {
        RefundQuote quote = refundPolicy.quote(NOW.plusDays(3), NOW, 1, UNIT_PRICE);

        assertThat(quote.feePercent()).isEqualTo(20);
        assertThat(quote.refundAmount()).isEqualTo(8_000);
    }

    @Test
    @DisplayName("공연 2일 전이면 위약금 30%를 뗀다")
    void 이틀_전은_위약금_30퍼센트() {
        RefundQuote quote = refundPolicy.quote(NOW.plusDays(2), NOW, 1, UNIT_PRICE);

        assertThat(quote.feePercent()).isEqualTo(30);
        assertThat(quote.refundAmount()).isEqualTo(7_000);
    }

    @Test
    @DisplayName("공연 시작까지 정확히 24시간 남았으면 아직 환불할 수 있다")
    void 정확히_24시간_전은_환불_가능() {
        RefundQuote quote = refundPolicy.quote(NOW.plusHours(24), NOW, 1, UNIT_PRICE);

        assertThat(quote.refundable()).isTrue();
        assertThat(quote.feePercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("공연 시작까지 24시간이 안 남았으면 환불할 수 없다 — 팀 결정")
    void 스물네시간_이내는_환불_불가() {
        RefundQuote quote = refundPolicy.quote(NOW.plusHours(23), NOW, 1, UNIT_PRICE);

        assertThat(quote.refundable()).isFalse();
        assertThat(quote.rejectReason()).isEqualTo(RefundPolicy.REJECT_ALREADY_STARTED_OR_CUTOFF);
        assertThat(quote.refundAmount()).isZero();
    }

    @Test
    @DisplayName("이미 시작한 공연은 환불할 수 없다")
    void 이미_시작한_공연은_환불_불가() {
        RefundQuote quote = refundPolicy.quote(NOW.minusHours(1), NOW, 1, UNIT_PRICE);

        assertThat(quote.refundable()).isFalse();
    }

    @Test
    @DisplayName("여러 장을 환불하면 장수만큼 금액이 곱해진다")
    void 부분_환불_수량만큼_계산된다() {
        RefundQuote quote = refundPolicy.quote(NOW.plusDays(9), NOW, 3, UNIT_PRICE);

        assertThat(quote.quantity()).isEqualTo(3);
        assertThat(quote.grossAmount()).isEqualTo(30_000);
        assertThat(quote.feeAmount()).isEqualTo(3_000);
        assertThat(quote.refundAmount()).isEqualTo(27_000);
    }

    @Test
    @DisplayName("공연 시작 시각을 모르면 참가자에게 불리하게 추정하지 않고 전액 환불한다")
    void 시작_시각을_모르면_전액_환불() {
        RefundQuote quote = refundPolicy.quote(null, NOW, 1, UNIT_PRICE);

        assertThat(quote.refundable()).isTrue();
        assertThat(quote.feePercent()).isZero();
    }
}
