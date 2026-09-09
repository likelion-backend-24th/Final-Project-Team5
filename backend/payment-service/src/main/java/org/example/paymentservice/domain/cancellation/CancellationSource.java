package org.example.paymentservice.domain.cancellation;

/**
 * 이 취소를 우리가 요청했는지(API_REQUEST), 아니면 PortOne 대시보드 등 외부에서 발생한 걸
 * 웹훅·재조회로 뒤늦게 발견했는지(WEBHOOK_DISCOVERED) 구분한다. 가이드 4.6·9.4절.
 */
public enum CancellationSource {
    API_REQUEST,
    WEBHOOK_DISCOVERED
}
