package org.example.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    @Schema(description = "유저 고유 ID", example = "1")
    private Long id;
    @Schema(description = "내 이메일", example = "test@naver.com")
    private String username;
    @Schema(description = "내 이름", example = "홍길동")
    private String name;
    @Schema(description = "내 닉네임", example = "개발자")
    private String nickname;
    @Schema(description = "내 권한", example = "USER")
    private Role role;
    @Schema(description = "내 계정 상태", example = "ACTIVE")
    private AccountStatus status;
    @Schema(description = "내 가입일시", example = "2026-08-01T10:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "도우미(HELPER) 계정이 담당하는 페스티벌 ID. 그 외 역할은 null", example = "1")
    private Long festivalId;
}