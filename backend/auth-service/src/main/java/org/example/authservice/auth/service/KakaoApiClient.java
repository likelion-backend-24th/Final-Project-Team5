package org.example.authservice.auth.service;


import org.example.authservice.auth.dto.oauth.KakaoTokenResponse;
import org.example.authservice.auth.dto.oauth.KakaoUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoApiClient {

    //외부서버에 HTTP 요청 보내는 도구
    private final RestClient restClient = RestClient.create();

    //카카오가 공식적으로 정해둔 엑세스 토큰 발급용 API
    private static final String KAKAO_TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    //카카오가 공식적으로 제공하는 내 정보 조회 API주소
    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    // 인가 코드(code)를 카카오 엑세스토큰으로 교환하는 메서드
    public String getAccessToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");  //인가 코드 방식으로 토큰 요청
        body.add("client_id", clientId);  //fevalgo 앱의 REST API 키
        body.add("client_secret", clientSecret); //fevalgo 앱의 클라이언트 키
        body.add("redirect_uri", redirectUri); // 카카오 콘솔에 등록한 콜백 주소
        body.add("code", code); //콜백으로 받은 인가코드

        KakaoTokenResponse response = restClient.post()
                .uri(KAKAO_TOKEN_URL)  //카카오 토큰 발급 주소로 요청
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(KakaoTokenResponse.class);

        return response.getAccess_token(); // 필요한건 엑세스토큰이니까 이것만 리턴
    }


    // 엑세스토큰을 받아서 카카오한테 물어보고 정보돌려주는 메서드
    public KakaoUserInfoResponse getUserInfo(String kakaoAccessToken) {
        return restClient.get() //GET방식으로 보냄
                .uri(KAKAO_USER_INFO_URL)
                .header("Authorization", "Bearer " + kakaoAccessToken)
                .retrieve()
                .body(KakaoUserInfoResponse.class);
    }
}