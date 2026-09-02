package org.example.authservice.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    @Schema(description = "로그인용 이메일", example = "test@naver.com")
    @NotBlank(message = "이메일은 필수입니다.")
    private String username;

    @Schema(description = "비밀번호", example = "test1234")
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}