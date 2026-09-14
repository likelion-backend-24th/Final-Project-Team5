package org.example.reservationservice.domain;

/** CANCELLED 상태의 사유. EXPIRED는 만료 배치가, 나머지는 Payment-Service의 cancel 호출이 채운다. */
public enum CancelReason {
    EXPIRED,
    PAYMENT_FAILED,
    PAYMENT_TIMEOUT,
    USER_CANCELLED
}
