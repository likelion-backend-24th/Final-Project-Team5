package org.example.festivalservice.domain.helper;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Festival-Service ↔ Auth-Service 도우미 계정 내부 계약에 쓰는 DTO 모음.
 */
public final class HelperAccountDto {

    private HelperAccountDto() {
    }

    // HOST가 신규 초대와 기존 계정 전환에 입력하는 연락 이메일.
    public record InviteRequest(
            @NotBlank
            @Email
            @Size(max = 254)
            String email
    ) {
        public InviteRequest {
            email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        }
    }

    // 소유권 검증을 마친 행사 스냅샷과 연락 이메일을 Auth에 전달한다.
    public record CreateRequest(
            Long festivalId,
            String festivalName,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime festivalStartAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            LocalDateTime festivalEndAt,
            String email
    ) {
    }

    // 발급 아이디와 초대 상태만 전달하며 비밀번호나 원문 토큰은 포함하지 않는다.
    public record HelperAccount(
            Long helperUserId,
            String username,
            String email,
            String status,
            String deliveryStatus,
            LocalDateTime sentAt,
            LocalDateTime lastSentAt,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            boolean legacy
    ) {
    }

    // HOST 도우미 관리 목록의 개수와 계정별 상태.
    public record SummaryResponse(
            int totalCount,
            List<HelperAccount> helpers
    ) {
    }
}
