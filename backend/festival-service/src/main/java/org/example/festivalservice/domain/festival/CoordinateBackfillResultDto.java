package org.example.festivalservice.domain.festival;

/** 좌표 백필 1건 처리 결과 — 적용됐으면 좌표까지, 건너뛰었으면 사유를 담는다. */
public record CoordinateBackfillResultDto(
        Long festivalId,
        String festivalName,
        boolean applied,
        String message,
        Double latitude,
        Double longitude
) {
    public static CoordinateBackfillResultDto applied(Long festivalId, String festivalName, double latitude, double longitude) {
        return new CoordinateBackfillResultDto(festivalId, festivalName, true, "좌표 적용됨", latitude, longitude);
    }

    public static CoordinateBackfillResultDto skipped(Long festivalId, String festivalName, String reason) {
        return new CoordinateBackfillResultDto(festivalId, festivalName, false, reason, null, null);
    }
}
