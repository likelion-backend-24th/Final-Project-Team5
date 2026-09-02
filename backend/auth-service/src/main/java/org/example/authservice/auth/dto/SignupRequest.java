package org.example.authservice.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SignupRequest {
    @Schema(description = "사용자 이름", example = "홍길동")
    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @Schema(description = "로그인용 이메일", example = "test@naver.com")
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String username;

    @Schema(description = "닉네임 (중복불가)", example = "안양개발자")
    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;

    @Schema(description = "비밀번호 (최소 8자)", example = "test1234")
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 최소 8자 ")
    private String password;

    //나중에 약관동의 쓸값
//    @Schema(description = "이용약관 및 개인정보처리방침 동의 여부 (필수 동의)", example = "true")
//    private boolean termsAgreed;
}
