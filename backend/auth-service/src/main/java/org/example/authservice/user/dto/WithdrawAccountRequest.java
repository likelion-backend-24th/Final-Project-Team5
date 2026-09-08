package org.example.authservice.user.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WithdrawAccountRequest {

    @Schema(description = "회원탈퇴 시 본인 확인용 비밀번호", example = "test1234")
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
