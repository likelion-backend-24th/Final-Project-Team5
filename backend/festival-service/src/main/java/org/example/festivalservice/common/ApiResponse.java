package org.example.festivalservice.common;

/** 팀에서 합의한 공통 응답 봉투: {success, data, message, code}. */
public record ApiResponse<T>(boolean success, T data, String message, String code) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> failure(String message, String code) {
        return new ApiResponse<>(false, null, message, code);
    }
}
