package org.example.authservice.helper.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Festival-Service → Auth-Service 내부 계약: POST /internal/v1/helper-accounts.
 * 호스트 소유권 검증은 이미 festival-service에서 끝났다고 보고, 여기서는 계정 발급만 담당한다.
 */
public record CreateHelperAccountRequest(
        @NotNull Long festivalId,
        //페스티벌 종료 시각 — 자동 탈퇴 배치가 쓰는 기준값이라 계정에 스냅샷으로 저장한다.
        @NotNull LocalDateTime festivalEndAt
) {
}
