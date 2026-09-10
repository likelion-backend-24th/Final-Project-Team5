package org.example.paymentservice.domain.cancellation;

/**
 * PortOne V2 결제 연동 실전 가이드 4.6절의 취소 상태 모델.
 * PortOne이 내려주는 취소 상태 문자열과 이름을 맞춰 두어 매핑 실수를 줄인다.
 */
public enum CancellationStatus {
    REQUESTED,
    PENDING,
    SUCCEEDED,
    FAILED;

    /**
     * 가이드 9.5 "순서 역전 방지" — 이미 확정(SUCCEEDED/FAILED)된 취소를 늦게 도착한
     * PENDING·REQUESTED 이벤트가 되돌리지 못하게 한다.
     */
    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
