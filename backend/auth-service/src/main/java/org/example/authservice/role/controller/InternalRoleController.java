package org.example.authservice.role.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.exception.AuthErrorCode;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.role.dto.GrantRoleRequest;
import org.example.authservice.role.service.RoleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gateway를 거치지 않는 내부 전용 API — 관계별 환경변수 Bearer Token으로만 인증한다. */
@RestController
@RequestMapping("/internal/v1/roles")
@RequiredArgsConstructor
public class InternalRoleController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final RoleService roleService;

    @Value("${internal.role-grant.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    @PutMapping
    public ResponseEntity<ApiResponse<Void>> grantRole(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody GrantRoleRequest request
    ) {
        if (!authorization.equals(BEARER_PREFIX + internalAuthToken)) {
            throw new ApiException(AuthErrorCode.INVALID_INTERNAL_TOKEN);
        }
        roleService.grantRole(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Role 부여 성공"));
    }
}
