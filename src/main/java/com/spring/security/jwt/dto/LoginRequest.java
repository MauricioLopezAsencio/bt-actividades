package com.spring.security.jwt.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    // Acepta tanto "username" como "user" en el JSON entrante.
    @JsonAlias("user")
    @NotBlank(message = "username es requerido")
    private String username;

    @NotBlank(message = "password es requerido")
    private String password;
}
