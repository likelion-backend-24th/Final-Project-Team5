package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.helper.HelperAccountDto;
import org.example.festivalservice.domain.helper.HelperAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 주최자가 페스티벌 관리 화면의 '도우미 계정 생성' 탭에서 쓰는 API.
 * 도우미 계정은 이 경로로만 만들어지며, 일반 회원가입으로는 HELPER 권한을 얻을 수 없다.
 */
@RestController
@RequestMapping("/api/host/festivals/{festivalId}/helpers")
@RequiredArgsConstructor
public class HostHelperAccountController {

    private final HelperAccountService helperAccountService;

    //도우미 계정 1개 발급 — 응답의 비밀번호는 이때 한 번만 보여주므로 프론트에서 반드시 노출해야 한다.
    @PostMapping
    public ResponseEntity<ApiResponse<HelperAccountDto.CredentialResponse>> create(
            @PathVariable Long festivalId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("도우미 계정 발급 성공",
                helperAccountService.createHelperAccount(festivalId, userId, role)));
    }

    //비밀번호 재발급 — 비밀번호를 분실했을 때 계정을 새로 만들지 않고 비밀번호만 새로 받는다.
    @PostMapping("/{helperUserId}/password")
    public ResponseEntity<ApiResponse<HelperAccountDto.CredentialResponse>> reissuePassword(
            @PathVariable Long festivalId,
            @PathVariable Long helperUserId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(ApiResponse.success("도우미 계정 비밀번호 재발급 성공",
                helperAccountService.reissuePassword(festivalId, helperUserId, userId, role)));
    }

    //발급해둔 도우미 계정 목록(개수 확인용) — 비밀번호는 포함되지 않는다.
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
