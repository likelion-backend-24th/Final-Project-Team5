package org.example.authservice.helper.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.common.dto.ApiResponse;
import org.example.authservice.common.exception.ApiException;
import org.example.authservice.helper.dto.*;
import org.example.authservice.helper.exception.HelperErrorCode;
import org.example.authservice.helper.service.HelperAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController @RequestMapping("/internal/v1/helper-accounts") @RequiredArgsConstructor
public class InternalHelperAccountController {
    private final HelperAccountService helperAccountService;
    @Value("${internal.role-grant.token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;
    @PostMapping
    public ApiResponse<HelperAccountSummaryResponse.HelperAccount> create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody CreateHelperAccountRequest request) {
        verifyInternalToken(authorization);
        return ApiResponse.success("도우미 초대 발송 성공", helperAccountService.createHelperAccount(request));
    }
    @PostMapping("/{helperUserId}/invitation")
    public ApiResponse<HelperAccountSummaryResponse.HelperAccount> convert(@PathVariable Long helperUserId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @Valid @RequestBody CreateHelperAccountRequest request) {
        verifyInternalToken(authorization);
        return ApiResponse.success("도우미 전환 초대 발송 성공", helperAccountService.convertLegacy(helperUserId, request));
    }
    @PostMapping("/{helperUserId}/resend")
    public ApiResponse<HelperAccountSummaryResponse.HelperAccount> resend(@PathVariable Long helperUserId, @RequestParam Long festivalId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        verifyInternalToken(authorization);
        return ApiResponse.success("도우미 초대 재발송 성공", helperAccountService.resend(festivalId, helperUserId));
    }
    @DeleteMapping("/{helperUserId}")
    public ApiResponse<HelperAccountSummaryResponse.HelperAccount> revoke(@PathVariable Long helperUserId, @RequestParam Long festivalId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        verifyInternalToken(authorization);
        return ApiResponse.success("도우미 계정 해지 성공", helperAccountService.revoke(festivalId, helperUserId));
    }
    @GetMapping
    public ApiResponse<HelperAccountSummaryResponse> list(@RequestParam Long festivalId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        verifyInternalToken(authorization);
        return ApiResponse.success("도우미 목록 조회 성공", helperAccountService.listHelperAccounts(festivalId));
    }
    @GetMapping("/session")
    public ApiResponse<Boolean> session(@RequestParam Long userId, @RequestParam Long festivalId, @RequestParam(defaultValue = "0") long version,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        verifyInternalToken(authorization);
        helperAccountService.validateSession(userId, festivalId, version);
        return ApiResponse.success("유효한 도우미 세션", true);
    }
    private void verifyInternalToken(String authorization) {
        if (!MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8), ("Bearer " + internalAuthToken).getBytes(StandardCharsets.UTF_8)))
            throw new ApiException(HelperErrorCode.INVALID_INTERNAL_TOKEN);
    }
}
