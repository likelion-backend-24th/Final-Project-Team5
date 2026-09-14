package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.helper.HelperAccountDto;
import org.example.festivalservice.domain.helper.HelperAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 주최자 본인 행사의 도우미 이메일 초대를 관리한다. */
@RestController
@RequestMapping("/api/host/festivals/{festivalId}/helpers")
@RequiredArgsConstructor
public class HostHelperAccountController {

    private final HelperAccountService helperAccountService;

    @PostMapping
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> create(
            @PathVariable Long festivalId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody HelperAccountDto.InviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("도우미 초대 발송 성공",
                helperAccountService.createHelperAccount(festivalId, userId, role, request.email())));
    }

    @PostMapping("/{helperUserId}/resend")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> resend(@PathVariable Long festivalId,
            @PathVariable Long helperUserId, @RequestHeader("X-User-Id") Long userId, @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("도우미 초대 재발송 성공", helperAccountService.resend(festivalId, helperUserId, userId, role)));
    }
    @PostMapping("/{helperUserId}/invitation")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> convert(@PathVariable Long festivalId,
            @PathVariable Long helperUserId, @RequestHeader("X-User-Id") Long userId, @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody HelperAccountDto.InviteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("도우미 전환 초대 발송 성공", helperAccountService.convertLegacy(festivalId, helperUserId, userId, role, request.email())));
    }
    @DeleteMapping("/{helperUserId}")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> revoke(@PathVariable Long festivalId,
            @PathVariable Long helperUserId, @RequestHeader("X-User-Id") Long userId, @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 해지 성공", helperAccountService.revoke(festivalId, helperUserId, userId, role)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HelperAccountDto.SummaryResponse>> list(
            @PathVariable Long festivalId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 목록 조회 성공",
                helperAccountService.listHelperAccounts(festivalId, userId, role)));
    }
}
