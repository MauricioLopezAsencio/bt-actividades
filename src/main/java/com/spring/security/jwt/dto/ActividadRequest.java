package com.spring.security.jwt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ActividadRequest {

    @NotBlank(message = "tokenMicrosoft es requerido")
    private String tokenMicrosoft;

    @NotBlank(message = "username es requerido")
    private String username;

    @NotBlank(message = "password es requerido")
    private String password;

    @NotNull(message = "fechaInicio es requerida")
    private LocalDate fechaInicio;

    @NotNull(message = "fechaFin es requerida")
    private LocalDate fechaFin;
}
