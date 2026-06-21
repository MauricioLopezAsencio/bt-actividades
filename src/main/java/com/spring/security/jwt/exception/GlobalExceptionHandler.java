package com.spring.security.jwt.exception;

import com.spring.security.jwt.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        final Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> Optional.ofNullable(fe.getDefaultMessage()).orElse("inválido"),
                        (a, b) -> a
                ));
        log.warn("Validación fallida path={} errors={}", request.getRequestURI(), errors);
        return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.validationError(errors).toBuilder()
                        .path(request.getRequestURI()).build());
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ApiResponse<Void>> handleNegocio(
            NegocioException ex, HttpServletRequest request) {
        log.warn("Regla de negocio violada path={} mensaje={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(409)
                .body(ApiResponse.error(409, ex.getMessage(), "NEGOCIO_ERROR").toBuilder()
                        .path(request.getRequestURI()).build());
    }

    @ExceptionHandler(TokenExpiradoException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenExpirado(
            TokenExpiradoException ex, HttpServletRequest request) {
        log.warn("Token expirado path={} mensaje={}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(401)
                .body(ApiResponse.error(401, ex.getMessage(), "TOKEN_EXPIRADO").toBuilder()
                        .path(request.getRequestURI()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Error no manejado path={}", request.getRequestURI(), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(500, "Error interno del servidor", "INTERNAL_ERROR").toBuilder()
                        .path(request.getRequestURI()).build());
    }
}
