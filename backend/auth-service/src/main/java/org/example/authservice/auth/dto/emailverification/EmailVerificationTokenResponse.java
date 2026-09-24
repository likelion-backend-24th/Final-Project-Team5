package org.example.authservice.auth.dto.emailverification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailVerificationTokenResponse {

    @Schema(description = "인증 성공 시 발급하는 인증 토큰 (비밀번호 재설정 요청에 함께 보냄, 10분 유효)", example = "q3Zx9Lk2...")
    private String verificationToken;
}
