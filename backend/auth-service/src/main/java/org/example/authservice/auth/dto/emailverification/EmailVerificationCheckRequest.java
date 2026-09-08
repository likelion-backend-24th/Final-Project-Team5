package org.example.authservice.auth.dto.emailverification;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailVerificationCheckRequest {

    @Schema(description = "인증 받을 실제 이메일", example = "csh980930@naver.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @Schema(description = "받은 인증코드 6자리", example = "384720")
    @NotBlank(message = "인증코드는 필수입니다.")
    private String code;
}
