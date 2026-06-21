package com.spring.security.jwt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstadisticasRequest {

    @NotBlank(message = "username es requerido")
    private String username;

    @NotBlank(message = "password es requerido")
    private String password;

    @NotNull(message = "mes es requerido")
    @Min(value = 1, message = "mes debe ser entre 1 y 12")
    @Max(value = 12, message = "mes debe ser entre 1 y 12")
    private Integer mes;

    @NotNull(message = "anio es requerido")
    @Min(value = 2020, message = "anio debe ser mayor a 2020")
    private Integer anio;
}
