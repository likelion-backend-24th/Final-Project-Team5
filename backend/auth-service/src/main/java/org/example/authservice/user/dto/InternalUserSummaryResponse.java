package org.example.authservice.user.dto;

import org.example.authservice.user.entity.User;

//다른 서비스(festival-service 등)가 userId만 들고 있을 때 화면에 보여줄 최소 신원 정보
public record InternalUserSummaryResponse(
        Long id,
        String username,
        String name,
        String nickname
) {
    public static InternalUserSummaryResponse from(User user) {
        return new InternalUserSummaryResponse(user.getId(), user.getUsername(), user.getName(), user.getNickname());
    }
}
