package org.example.authservice.admin.dto;

import java.time.LocalDateTime;

import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.User;

/**
 * 운영자 주최자 목록에 노출할 Auth 소유 계정 정보다.
 */
public record AdminHostResponse(
        Long id,
        String nickname,
        String email,
        AccountStatus accountStatus,
        LocalDateTime joinedAt,
        String suspendReason,
        LocalDateTime suspendedAt
) {
    public static AdminHostResponse from(User user) {
        return new AdminHostResponse(
                user.getId(),
                user.getNickname(),
                user.getUsername(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getSuspendReason(),
                user.getSuspendedAt()
        );
    }
}
