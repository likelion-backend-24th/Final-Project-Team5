package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.helper.HelperAccountDto;
import org.example.festivalservice.domain.helper.HelperAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 주최자 본인 행사의 도우미 이메일 초대를 관리한다. */
@RestController
@RequestMapping("/api/host/festivals/{festivalId}/helpers")
@RequiredArgsConstructor
public class HostHelperAccountController {

    private final HelperAccountService helperAccountService;

    // 신규 HELPER 계정을 만들고 연락 이메일로 활성화 링크를 보낸다.
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

    // 대기 중인 초대의 링크를 교체해 다시 보낸다.
    @PostMapping("/{helperUserId}/resend")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> resend(
            @PathVariable Long festivalId,
            @PathVariable Long helperUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("도우미 초대 재발송 성공",
                helperAccountService.resend(festivalId, helperUserId, userId, role)));
    }

    // 기존 계정을 이메일 초대 방식으로 전환할 링크를 보낸다.
    @PostMapping("/{helperUserId}/invitation")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> convert(
            @PathVariable Long festivalId,
            @PathVariable Long helperUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody HelperAccountDto.InviteRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("도우미 전환 초대 발송 성공",
                helperAccountService.convertLegacy(festivalId, helperUserId, userId, role, request.email())));
    }

    // 초대와 계정을 해지하고 기존 세션을 폐기한다.
    @DeleteMapping("/{helperUserId}")
    public ResponseEntity<ApiResponse<HelperAccountDto.HelperAccount>> revoke(
            @PathVariable Long festivalId,
            @PathVariable Long helperUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 해지 성공",
                helperAccountService.revoke(festivalId, helperUserId, userId, role)));
    }

    // 본인 행사의 도우미 계정과 초대 상태를 조회한다.
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
