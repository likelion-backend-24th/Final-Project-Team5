package org.example.authservice.auth.dto;

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
public class OauthLinkConfirmRequest {

    @Schema(description = "기존 계정 연동 동의용 임시 토큰(구글 콜백 리다이렉트에 담겨 온다)")
    @NotBlank(message = "토큰은 필수입니다.")
    private String token;
}
