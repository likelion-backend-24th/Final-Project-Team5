package org.example.festivalservice.domain.festival;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

public enum FestivalStatus {
    PENDING,
    PUBLISHED,
    REJECTED,
    //종료 일시(endAt)가 지난 PUBLISHED 페스티벌을 배치가 이 상태로 전환한다. 목록/상세에는 계속 노출되지만
    //예매 신청은 PUBLISHED가 아니므로 자동으로 막힌다(reservation-service 쪽 코드 변경 불필요).
    CLOSED
}