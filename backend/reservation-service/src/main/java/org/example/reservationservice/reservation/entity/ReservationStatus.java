package org.example.reservationservice.domain;

/** 결제 진행 중(Story 7)·환불(Story 9)까지 아우르는 상태. REFUNDED/PARTIALLY_REFUNDED는 값만 두고 로직은 Story 9에서 채운다. */
public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
