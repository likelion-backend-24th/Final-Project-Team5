package org.example.authservice.auth.service;

import org.example.authservice.auth.dto.oauth.GoogleTokenResponse;
import org.example.authservice.auth.dto.oauth.GoogleUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;


@Component
public class GoogleApiClient {
    //외부서버에 HTTP 요청 보내는 도구
    private final RestClient restClient = RestClient.create();

    // 구글이 공식적으로 정해둔 엑세스 토큰 발급용 API
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    // 구글이 공식적으로 제공하는 내 정보 조회 API 주소
    private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    // 인가 코드(code)를 구글 엑세스토큰으로 교환하는 메서드
    public String getAccessToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code"); //인가 코드 방식 토큰 요청
        body.add("client_id", clientId);    //fevalgo 앱의 클라이언트키
        body.add("client_secret", clientSecret); // 시크릿키
        body.add("redirect_uri", redirectUri); // 콜백주소
        body.add("code", code); //콜백으로 받은 인가 코드

        GoogleTokenResponse response = restClient.post() //post방식요청
                .uri(GOOGLE_TOKEN_URL)//구글 토큰 발급 주소로 보냄
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(GoogleTokenResponse.class);

        return response.getAccess_token();
    }

    // 엑세스토큰을 받아서 구글한테 물어보고 정보 돌려주는 메서드
    public GoogleUserInfoResponse getUserInfo(String googleAccessToken) {
        return restClient.get()
                .uri(GOOGLE_USER_INFO_URL)
                .header("Authorization", "Bearer " + googleAccessToken)
                .retrieve()
                .body(GoogleUserInfoResponse.class);
    }
}