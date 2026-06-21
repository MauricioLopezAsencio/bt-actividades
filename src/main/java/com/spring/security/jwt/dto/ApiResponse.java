package com.spring.security.jwt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;

    @Builder.Default
    private final String transactionId = UUID.randomUUID().toString();

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final String message;
    private final T data;
    private final String errorCode;
    private final Map<String, String> validationErrors;
    private final String path;

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder()
                .status(200).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return ApiResponse.<T>builder()
                .status(201).data(data).message(message).build();
    }

    public static ApiResponse<Void> error(int status, String message, String errorCode) {
        return ApiResponse.<Void>builder()
                .status(status).message(message).errorCode(errorCode).build();
    }

    public static ApiResponse<Void> validationError(Map<String, String> errors) {
        return ApiResponse.<Void>builder()
                .status(422).message("Validación fallida").validationErrors(errors).build();
    }
}
