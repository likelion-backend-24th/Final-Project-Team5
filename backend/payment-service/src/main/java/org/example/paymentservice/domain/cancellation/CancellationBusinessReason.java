package org.example.paymentservice.domain.cancellation;

// RESERVATION_NOT_CONFIRMED: 결제는 승인됐지만 예매 확정이 거절된 결제의 자동 전액 환불(보상, 실전 가이드 7.4·11.4)
public enum CancellationBusinessReason {USER_REQUEST, ORGANIZER_FAULT, ADMIN_CORRECTION, RESERVATION_NOT_CONFIRMED}
