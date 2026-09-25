package org.example.authservice.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminUserSuspendRequest {

    @NotBlank(message = "정지 사유를 입력해주세요.")
    @Size(max = 255, message = "정지 사유는 255자 이하로 입력해주세요.")
    private String reason;
}