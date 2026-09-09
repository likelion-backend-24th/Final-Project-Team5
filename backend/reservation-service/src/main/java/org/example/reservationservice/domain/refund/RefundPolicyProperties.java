package org.example.reservationservice.domain.refund;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 환불 위약금 정책 설정. 사이트 정책 점검 5번 팀 결정("기본적으로는 법률에 따름, 24시간 내는 환불 불가")을
 * 공연 관람권 표준약관류의 단계별 위약금 표로 옮긴 것이다.
 *
 * 팀 결정 당시 구간별 정확한 수치는 "팀이 검색해 확인 권장"으로 비어 있었기 때문에, 코드가 아니라
 * 설정으로 두어 수치만 바꿀 수 있게 한다.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "refund")
public class RefundPolicyProperties {

    /** 공연 시작까지 남은 시간이 이 값 미만이면 환불 자체를 막는다(팀 결정: 24시간). */
    private long cutoffHours = 24;

    /** 남은 일수 구간별 위약금 비율. daysBefore 내림차순으로 정렬해 사용한다. */
    private List<Tier> tiers = List.of();

    @Getter
    @Setter
    public static class Tier {
        /** 공연 시작까지 남은 일수가 이 값 이상일 때 적용된다. */
        private int daysBefore;
        /** 위약금 비율(%). 0이면 전액 환불. */
        private int feePercent;
    }
}
