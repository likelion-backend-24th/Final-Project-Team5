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

    //STOREHOST가 "다음 순번 호출"을 누를 때마다 증가하는, 현재까지 호출된 번호. 0이면 아직 아무도
    //호출하지 않은 상태다. nextNumber(지금까지 발급된 마지막 번호)를 넘어서 호출할 수는 없다.
    @Column(name = "called_number", nullable = false)
    private int calledNumber;

    public BoothWaitlistCounter(Long boothId) {
        this.boothId = boothId;
        this.nextNumber = 0;
        this.calledNumber = 0;
    }
}
