package org.example.authservice.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NicknameUpdateRequest {

    @Schema(description = "변경할 새 닉네임", example = "개발자")
    @NotBlank(message = "닉네임은 필수입니다.")
    private String nickname;
}
