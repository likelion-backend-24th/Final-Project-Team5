package org.example.authservice.helper.dto;

/**
 * 도우미 계정 발급·재발급 응답. 평문 비밀번호는 이 응답에서 딱 한 번만 노출되고 저장은 해시로만 남으므로,
 * 호스트가 이 값을 놓치면 다시 조회할 수 없고 재발급을 받아야 한다.
 */
public record HelperAccountCredentialResponse(
        Long helperUserId,
        String username,
        String password
) {
}
