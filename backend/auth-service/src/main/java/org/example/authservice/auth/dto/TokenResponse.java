package org.example.authservice.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TokenResponse {
    @Schema(description = "JWT 엑세스 토큰")
    private String accessToken;
    @Schema(hidden = true)  // 스키마 문서에도 필드 숨김
    @JsonIgnore  // HttpOnly 쿠키로 내려받을거임
    private String refreshToken;
}