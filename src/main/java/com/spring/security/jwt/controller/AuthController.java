package com.spring.security.jwt.controller;

import com.spring.security.jwt.dto.ApiResponse;
import com.spring.security.jwt.dto.LoginRequest;
import com.spring.security.jwt.dto.LoginResponseDto;
import com.spring.security.jwt.service.BitacoraTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final BitacoraTokenManager tokenManager;

    public AuthController(BitacoraTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    /**
     * Autentica contra el SCO (https://scoca.casystem.com.mx/api/auth/login)
     * y devuelve el token + idEmpleado.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {

        try {
            LoginResponseDto data = tokenManager.autenticar(
                    request.getUsername(), request.getPassword());

            log.info("Login exitoso username={}", request.getUsername());
            return ResponseEntity.ok(ApiResponse.ok(data, "Autenticación exitosa")
                    .toBuilder().path(servletRequest.getRequestURI()).build());

        } catch (HttpClientErrorException ex) {
            // Credenciales inválidas u otro 4xx devuelto por el SCO
            log.warn("Login rechazado username={} status={}", request.getUsername(), ex.getStatusCode());
            return ResponseEntity.status(401)
                    .body(ApiResponse.<LoginResponseDto>builder()
                            .status(401)
                            .message("Usuario o contraseña incorrectos")
                            .errorCode("CREDENCIALES_INVALIDAS")
                            .path(servletRequest.getRequestURI())
                            .build());
        }
    }
}
