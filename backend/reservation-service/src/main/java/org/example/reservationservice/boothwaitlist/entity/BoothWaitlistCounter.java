package org.example.reservationservice.boothwaitlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부스별 다음 대기번호 카운터. 동시에 여러 명이 신청해도 같은 번호가 나가지 않도록, 번호 발급은
 * "UPDATE ... SET next_number = next_number + 1" 원자적 증가로만 한다(TicketType 재고 차감과 같은 패턴).
 * 첫 신청 시점에 행이 없으면 그때 만든다 — BoothWaitlistService.ensureCounterExists() 참고.
 */
@Entity
@Table(name = "booth_waitlist_counters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoothWaitlistCounter {

    @Id
    @Column(name = "booth_id")
    private Long boothId;

    @Column(name = "next_number", nullable = false)
    private int nextNumber;

    public BoothWaitlistCounter(Long boothId) {
        this.boothId = boothId;
        this.nextNumber = 0;
    }
}
