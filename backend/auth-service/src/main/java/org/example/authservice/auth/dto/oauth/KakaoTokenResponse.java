package org.example.authservice.auth.dto.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoTokenResponse { //카카오 토큰 발급 API 응답을 받을 그릇
    private String access_token;  //이것만 필요한데 밑에거는 카카오에서 주는 응답형식때메 받는거임
    private String token_type;
    private String refresh_token;
    private Integer expires_in;
}