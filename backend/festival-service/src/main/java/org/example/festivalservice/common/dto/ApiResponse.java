package org.example.festivalservice.common.dto;

import lombok.Getter;

/** 팀에서 합의한 공통 응답 봉투: {success, data, message, code}. 페이징 목록 응답은 meta를 추가로 포함. */
@Getter
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final Meta meta;
    private final String message;
    private final String errorCode;

    private ApiResponse(boolean success, String message, T data, Meta meta, String errorCode) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.meta = meta;
        this.errorCode = errorCode;
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data,null, null);
    }

    public static <T> ApiResponse<T> success(String message, T data, Meta meta) {
        return new ApiResponse<>(true, message, data, meta, null);
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, message, null,null, errorCode);
    }
}
