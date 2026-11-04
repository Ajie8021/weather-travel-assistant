package com.example.wea_trav_assistant.dto;

import java.time.LocalDateTime;

/** Response envelope used by newly added JSON APIs. */
public record ApiResponse<T>(int code, String message, T data, LocalDateTime timestamp) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, LocalDateTime.now());
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }
}
