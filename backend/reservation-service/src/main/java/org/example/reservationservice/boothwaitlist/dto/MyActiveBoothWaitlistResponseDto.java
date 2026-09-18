package org.example.reservationservice.boothwaitlist.dto;

/** 참가자가 신청한 부스 대기 1건 — 챗봇 위젯이 폴링해서 내 차례가 됐는지(myTurn) 판단하는 데 쓴다. */
public record MyActiveBoothWaitlistResponseDto(
        Long boothId,
        Long festivalId,
        int queueNumber,
        int calledNumber,
        boolean myTurn
) {
    public static MyActiveBoothWaitlistResponseDto of(Long boothId, Long festivalId, int queueNumber, int calledNumber) {
        return new MyActiveBoothWaitlistResponseDto(boothId, festivalId, queueNumber, calledNumber, calledNumber >= queueNumber);
    }
}
