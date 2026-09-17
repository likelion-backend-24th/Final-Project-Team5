package org.example.reservationservice.boothwaitlist.dto;

/** STOREHOST가 본인 부스의 대기열 현황을 보는 응답. waitingCount = issuedNumber - calledNumber. */
public record BoothWaitlistQueueStatusResponseDto(
        int calledNumber,
        int issuedNumber,
        int waitingCount
) {
    public static BoothWaitlistQueueStatusResponseDto of(int calledNumber, int issuedNumber) {
        return new BoothWaitlistQueueStatusResponseDto(calledNumber, issuedNumber, issuedNumber - calledNumber);
    }
}
