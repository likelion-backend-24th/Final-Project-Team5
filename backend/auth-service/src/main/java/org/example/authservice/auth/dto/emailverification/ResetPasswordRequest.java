package org.example.authservice.auth.dto.emailverification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @Schema(description = "비밀번호를 재설정할 이메일 (인증 완료된 이메일이어야 함) ",example = "test@naver.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String username;

    @Schema(description = "새 비밀번호 (최소 8자)", example = "newpw1234")
    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야합니다.")
    private String newPassword;
}
