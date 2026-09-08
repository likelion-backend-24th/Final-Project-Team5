package org.example.authservice.auth.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.authservice.auth.dto.emailverification.EmailVerificationCheckRequest;
import org.example.authservice.auth.dto.emailverification.EmailVerificationRequest;
import org.example.authservice.auth.service.EmailVerificationService;
import org.example.authservice.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
@Tag(name="이메일 인증",description = "이메일 인증코드 발송 및 검증 API")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    //인증 코드 발송
    @Operation(summary = "인증코드 발송", description = "입력한 이메일로 6자리 인증코드를 발송합니다.")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<Void>> sendCode(
            @Valid @RequestBody EmailVerificationRequest request){
        emailVerificationService.sendCode(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("인증코드가 발송 되었습니다.",null));
    }

    //인증코드 검증
    @Operation(summary = "인증코드 검증", description = "받은 인증코드를 일치하는지 확인하여 검증합니다.")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyCode(
            @Valid @RequestBody EmailVerificationCheckRequest request){
        emailVerificationService.verifyCode(request.getEmail(),request.getCode());
        return ResponseEntity.ok(ApiResponse.success("이메일 인증이 완료되었습니다.",null));
    }

}
