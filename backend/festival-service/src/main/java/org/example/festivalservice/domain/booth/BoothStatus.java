package org.example.festivalservice.domain.booth;

public enum BoothStatus {
    //등록 직후 기본 상태. 페스티벌 관람자에게는 노출되지 않는다(목록·상세 모두 숨김).
    WAITING,
    //관람자에게 공개되어 대기 신청을 받을 수 있는 상태.
    OPEN,
    //대기 신청 마감. 관람자에게는 계속 노출되지만(운영 종료된 부스 확인용) 새 대기 신청은 막힌다.
    CLOSED
}
