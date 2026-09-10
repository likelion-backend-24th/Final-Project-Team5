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
public class GoogleTokenResponse {
    private String access_token;  //실제로 쓸건 엑세스토큰만이다 밑에 나머지는 형식적인거라 잘 안씀
    private String token_type;
    private String refresh_token;
    private Integer expires_in;
    private String scope;
    private String id_token;
}