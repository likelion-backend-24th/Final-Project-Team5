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

    //비밀번호 대신 동의 문구를 직접 입력받는다 — 소셜 전용 계정은 비밀번호가 없어 같은 절차로 탈퇴할 수 있어야 한다.
    public static final String CONFIRMATION_PHRASE = "회원 탈퇴에 동의합니다";

    @Schema(description = "탈퇴 동의 문구. 정확히 \"회원 탈퇴에 동의합니다\"를 입력해야 한다", example = CONFIRMATION_PHRASE)
    @NotBlank(message = "탈퇴 동의 문구는 필수입니다.")
    private String confirmation;
}
