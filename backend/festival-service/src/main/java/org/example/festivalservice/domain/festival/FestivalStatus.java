package org.example.festivalservice.domain.festival;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

public enum FestivalStatus {
    PENDING,
    //어드민 승인 결정 직후, reservation-service에 좌석 생성 요청을 보내는 중간 상태.
    //HostApplication의 APPROVAL_PENDING과 같은 패턴 — 좌석 생성 API 호출이 실패해도 이 상태로
    //안전하게 남아있다가 재시도 스케줄러가 나중에 PUBLISHED로 확정한다.
    PUBLISH_PENDING,
    PUBLISHED,
    REJECTED,
    //종료 일시(endAt)가 지난 PUBLISHED 페스티벌을 배치가 이 상태로 전환한다. 목록/상세에는 계속 노출되지만
    //예매 신청은 PUBLISHED가 아니므로 자동으로 막힌다(reservation-service 쪽 코드 변경 불필요).
    CLOSED,
    CANCELLATION_PENDING,
    CANCELLED
}
