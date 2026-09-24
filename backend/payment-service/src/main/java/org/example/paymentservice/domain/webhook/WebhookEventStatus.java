package org.example.paymentservice.domain.webhook;

/**
 * 수신한 웹훅의 처리 상태. 실전 가이드 8.6 권장 구현(Inbox 저장 → 처리 → PROCESSED 또는 RETRY)을 따른다.
 *
 * RECEIVED: 저장만 되고 처리가 끝나지 않았다(처리 중이거나, 처리 도중 서버가 멈췄다).
 * PROCESSED: 결제 동기화·취소 대사까지 끝났다.
 * FAILED: 일시 장애(타임아웃·5xx 등)로 처리하지 못했다 — 재전송이나 재시도 배치가 다시 처리한다.
 * IGNORED: 다시 처리해도 결과가 같다(다른 팀 결제, 검증 실패 등) — 재시도하지 않는다.
 */
public enum WebhookEventStatus {
    RECEIVED,
    PROCESSED,
    FAILED,
    IGNORED
}
