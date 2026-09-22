package org.example.festivalservice.domain.festival;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 등록 폼 "AI로 초안 채우기" 팝업에서 호스트가 자유롭게 입력한 한 줄~여러 줄 설명. */
public record FestivalAiDraftRequestDto(
        @NotBlank @Size(max = 500, message = "설명은 500자 이내로 입력해주세요.") String prompt
) {
}
