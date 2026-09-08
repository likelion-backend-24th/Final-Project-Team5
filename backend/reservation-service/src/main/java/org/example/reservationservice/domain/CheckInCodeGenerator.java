package org.example.reservationservice.domain;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * QR 스캔이 실패했을 때 도우미가 손으로 입력하는 입장 코드를 만든다.
 * 현장에서 모바일로 빠르게 옮겨 적는 값이라 2-4-4로 끊어 읽기 쉽게 하고,
 * 헷갈리는 글자(0/O, 1/I)를 뺀 대문자·숫자만 쓴다.
 */
@Component
public class CheckInCodeGenerator {

    //O/0, I/1 처럼 눈으로 구분이 어려운 글자를 제외한 대문자·숫자 집합
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int[] SEGMENT_LENGTHS = {2, 4, 4};
    private static final String SEGMENT_SEPARATOR = "-";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder();
        for (int segment = 0; segment < SEGMENT_LENGTHS.length; segment++) {
            if (segment > 0) {
                code.append(SEGMENT_SEPARATOR);
            }
            for (int i = 0; i < SEGMENT_LENGTHS[segment]; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return code.toString();
    }
}
