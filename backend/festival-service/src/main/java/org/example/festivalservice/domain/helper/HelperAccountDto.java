package org.example.festivalservice.domain.helper;

import java.time.LocalDateTime;
import java.util.List;

/** Festival-Service ↔ Auth-Service 도우미 계정 내부 계약에 쓰는 DTO 모음. */
public final class HelperAccountDto {

    private HelperAccountDto() {
    }

    /** POST /internal/v1/helper-accounts 요청. */
    public record CreateRequest(Long festivalId, LocalDateTime festivalEndAt) {
    }

    /**
     * 계정 발급·재발급 응답. 평문 비밀번호가 노출되는 유일한 지점이며 저장되지 않으므로,
     * 호스트가 이 값을 놓치면 재발급을 받아야 한다.
     */
    public record CredentialResponse(Long helperUserId, String username, String password) {
    }

    /** 발급된 계정 목록(개수 확인용). 비밀번호는 복원할 수 없어 포함되지 않는다. */
    public record SummaryResponse(int totalCount, List<HelperAccount> helpers) {

        public record HelperAccount(Long helperUserId, String username, LocalDateTime createdAt) {
        }
    }
}
