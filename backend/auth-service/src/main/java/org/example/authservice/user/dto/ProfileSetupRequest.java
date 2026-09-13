package org.example.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//소셜 로그인으로 처음 들어온 회원이 이름·닉네임을 정하고 약관에 동의하는 최초 1회 프로필 설정 요청
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProfileSetupRequest {

    @Schema(description = "이름", example = "홍길동")
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @Schema(description = "닉네임(2~12자)", example = "개발자")
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
    private String nickname;

    @Schema(description = "이용약관·개인정보처리방침 동의 여부", example = "true")
    private boolean termsAgreed;
}
