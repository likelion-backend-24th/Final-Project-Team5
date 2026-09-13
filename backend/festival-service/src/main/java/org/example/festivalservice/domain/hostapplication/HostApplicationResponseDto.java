package org.example.festivalservice.domain.hostapplication;

import java.time.LocalDateTime;
import org.example.festivalservice.common.UserLookupClient.UserSummary;

public record HostApplicationResponseDto(
        Long id,
        Long userId,
        HostApplicationStatus status,
        String introduction,
        String contact,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        //신청자 신원(auth-service 조회). 운영자 심사 목록에서만 채워지고, 조회 실패 시 null
        String applicantName,
        String applicantNickname,
        String applicantEmail
) {
    public static HostApplicationResponseDto from(HostApplication application) {
        return from(application, null);
    }

    public static HostApplicationResponseDto from(HostApplication application, UserSummary applicant) {
        return new HostApplicationResponseDto(
                application.getId(),
                application.getUserId(),
                application.getStatus(),
                application.getIntroduction(),
                application.getContact(),
                application.getRejectReason(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                applicant == null ? null : applicant.name(),
                applicant == null ? null : applicant.nickname(),
                applicant == null ? null : applicant.username()
        );
    }
}
