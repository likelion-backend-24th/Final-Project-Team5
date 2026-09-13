package org.example.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.authservice.user.entity.AccountStatus;
import org.example.authservice.user.entity.Role;

import java.time.LocalDateTime;
import java.util.List;

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
    @Schema(description = "연결된 소셜 로그인 제공자 목록(KAKAO/GOOGLE). 비어 있으면 이메일 가입 회원", example = "[\"KAKAO\"]")
    private List<String> socialProviders;
    @Schema(description = "비밀번호가 설정된 계정인지. 소셜 전용 계정은 false", example = "true")
    private boolean hasPassword;
    @Schema(description = "소셜 로그인으로 처음 들어와 아직 약관 동의·닉네임 설정을 하지 않았는지. true면 프로필 설정 화면으로 보내야 한다", example = "false")
    private boolean profileSetupRequired;
}