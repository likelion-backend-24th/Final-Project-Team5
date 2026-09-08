package org.example.authservice.helper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.CreateHelperAccountRequest;
import org.example.authservice.helper.dto.HelperAccountCredentialResponse;
import org.example.authservice.helper.dto.HelperAccountSummaryResponse;
import org.example.authservice.helper.exception.HelperErrorCode;
import org.example.authservice.helper.service.HelperAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Gateway를 거치지 않는 내부 전용 API — Festival-Service가 관계별 환경변수 Bearer Token으로만 인증해 호출한다.
 * 호스트가 정말 그 페스티벌의 주최자인지는 festival-service가 이미 확인한 뒤 넘어온다.
 */
@RestController
@RequestMapping("/internal/v1/helper-accounts")
@RequiredArgsConstructor
public class InternalHelperAccountController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final HelperAccountService helperAccountService;

    @Value("${internal.role-grant.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //Festival-Service → Auth-Service: 도우미 계정 1개 발급(평문 비밀번호는 이 응답에서만 노출)
    @PostMapping
    public ResponseEntity<ApiResponse<HelperAccountCredentialResponse>> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody CreateHelperAccountRequest request
    ) {
        verifyInternalToken(authorization);
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 발급 성공",
                helperAccountService.createHelperAccount(request)));
    }

    //Festival-Service → Auth-Service: 비밀번호 재발급(계정은 유지, 비밀번호만 새로 생성)
    @PostMapping("/{helperUserId}/password")
    public ResponseEntity<ApiResponse<HelperAccountCredentialResponse>> reissuePassword(
            @PathVariable Long helperUserId,
            @RequestParam Long festivalId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        verifyInternalToken(authorization);
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 비밀번호 재발급 성공",
                helperAccountService.reissuePassword(festivalId, helperUserId)));
    }

    //Festival-Service → Auth-Service: 발급된 도우미 계정 목록(개수 확인용, 비밀번호 미포함)
    @GetMapping
    public ResponseEntity<ApiResponse<HelperAccountSummaryResponse>> list(
            @RequestParam Long festivalId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        verifyInternalToken(authorization);
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 목록 조회 성공",
                helperAccountService.listHelperAccounts(festivalId)));
    }

    private void verifyInternalToken(String authorization) {
        if (!authorization.equals(BEARER_PREFIX + internalAuthToken)) {
            throw new ApiException(HelperErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}
