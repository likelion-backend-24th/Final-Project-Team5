package org.example.festivalservice.domain.tickettype;

public enum TicketMode {
    SEATED,    // 좌석 선택형 — zone/rows/seatsPerRow 필수, Seat 테이블이 재고 진실
    STANDING   // 스탠딩(수량제) — 기존 remainQuantity 원자적 차감 방식 그대로 유지
}