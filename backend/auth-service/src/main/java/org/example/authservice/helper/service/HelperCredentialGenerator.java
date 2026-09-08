package org.example.authservice.helper.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 도우미 계정의 로그인 정보를 만든다. 알바가 현장에서 모바일로 직접 입력하는 값이라 헷갈리는 글자
 * (0/O, 1/l/I)를 뺀 알파벳만 쓴다. 아이디는 기존 로그인 화면이 이메일 형식을 검증하므로 이메일 모양으로 만든다.
 */
@Component
public class HelperCredentialGenerator {

    //0/o, 1/l 처럼 눈으로 구분이 어려운 글자를 제외한 소문자·숫자 집합
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private static final String USERNAME_PREFIX = "helper-";
    private static final String USERNAME_DOMAIN = "@helper.local";
    private static final String NICKNAME_PREFIX = "도우미-";
    private static final int SUFFIX_LENGTH = 8;

    //SignupRequest의 @Size(min = 8)이 요구하는 최소 길이. 알바 입력 편의를 위해 그 이상 늘리지 않는다.
    private static final int PASSWORD_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generateUsername(String suffix) {
        return USERNAME_PREFIX + suffix + USERNAME_DOMAIN;
    }

    public String generateNickname(String suffix) {
        return NICKNAME_PREFIX + suffix;
    }

    public String generateSuffix() {
        return randomString(SUFFIX_LENGTH);
    }

    public String generatePassword() {
        return randomString(PASSWORD_LENGTH);
    }

    private String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
