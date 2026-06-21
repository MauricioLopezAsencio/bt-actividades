package com.spring.security.jwt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class RegistrarActividadRequest {

    @NotBlank(message = "username es requerido")
    private String username;

    @NotBlank(message = "password es requerido")
    private String password;

    @NotNull(message = "idActividad es requerido")
    private Long idActividad;

    @NotNull(message = "idTipoActividad es requerido")
    private Integer idTipoActividad;

    private Long idProyecto;

    @NotBlank(message = "descripcion es requerida")
    private String descripcion;

    @NotNull(message = "fechaRegistro es requerida")
    private LocalDate fechaRegistro;

    @NotBlank(message = "horaInicio es requerida")
    private String horaInicio;

    @NotBlank(message = "horaFin es requerida")
    private String horaFin;

    /**
     * Código de fase ({@link Fase}). Solo se envía a Scoca cuando el tipo de
     * actividad seleccionado es "Servicio". En los demás tipos se ignora.
     */
    private String fase;
}
