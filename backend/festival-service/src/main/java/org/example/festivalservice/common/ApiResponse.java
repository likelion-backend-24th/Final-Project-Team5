package org.example.festivalservice.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 팀에서 합의한 공통 응답 봉투: {success, data, message, code}. 페이징 목록 응답은 meta를 추가로 포함. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, String errorCode, Meta meta) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null, null);
    }

    public static <T> ApiResponse<T> success(T data, Meta meta, String message) {
        return new ApiResponse<>(true, data, message, null, meta);
    }

    public static ApiResponse<Void> failure(String message, String code) {
        return new ApiResponse<>(false, null, message, code, null);
    }
}
