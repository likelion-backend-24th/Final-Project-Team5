package org.example.authservice.helper.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 호스트가 자기 페스티벌에 몇 개의 도우미 계정을 발급했는지 확인하는 목록 응답.
 * 비밀번호는 해시로만 저장하므로 여기에 담기지 않는다 — 분실 시 재발급을 받아야 한다.
 */
public record HelperAccountSummaryResponse(
        int totalCount,
        List<HelperAccount> helpers
) {

    public record HelperAccount(
            Long helperUserId,
            String username,
            LocalDateTime createdAt
    ) {
    }
}
