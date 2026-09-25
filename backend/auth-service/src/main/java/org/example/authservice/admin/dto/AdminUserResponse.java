package org.example.authservice.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserResponse {

    private Long id;
    private String nickname;
    private String email;                 // User의 username(이메일)
    private Role role;
    private AccountStatus status;
    private List<String> providers;       // [] = 일반, ["KAKAO"] 등, null = 탈퇴(알 수 없음)
    private LocalDateTime joinedAt;       // User의 createdAt
    private String suspendReason;
    private LocalDateTime suspendedAt;
}