package org.example.authservice.auth.dto.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 카카오는 JSON이 삼단으로 와서 내부클래스 썼는데
// 구글은 JSON이 단순한구조라 내부클래스 쓸 필요없음
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) //이게 궁금하실텐데 이건 사진같은 안적어 놓은 필드가 와도 에러가 안나게 하는 안전장치
public class GoogleUserInfoResponse {
    private String id; //카카오는 Long인데 구글은 문자열로 와서 String입니다.
    private String email;
    private String name;
}
