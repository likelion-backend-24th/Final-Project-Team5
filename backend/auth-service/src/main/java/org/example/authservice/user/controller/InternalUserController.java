package org.example.authservice.user.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.role.exception.RoleErrorCode;
import org.example.authservice.user.dto.InternalUserSummaryResponse;
import org.example.authservice.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway를 거치지 않는 내부 전용 API — 다른 서비스가 userId 목록으로 이름·닉네임·이메일을 조회한다.
 * festival-service가 주최자 신청 심사 목록에 신청자 정보를 붙일 때 쓴다. 인증은 다른 내부 API와 같은 토큰.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final UserService userService;

    @Value("${internal.role-grant.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InternalUserSummaryResponse>>> findByIds(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("ids") List<Long> ids
    ) {
        if (!authorization.equals(BEARER_PREFIX + internalAuthToken)) {
            throw new ApiException(RoleErrorCode.INVALID_INTERNAL_TOKEN);
        }
        //사용자 조회 범위와 DTO 구성은 서비스에서 관리한다.
        List<InternalUserSummaryResponse> users = userService.findInternalUserSummaries(ids);
        return ResponseEntity.ok(ApiResponse.success("사용자 요약 조회 성공", users));
    }
}
