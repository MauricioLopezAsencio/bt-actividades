package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActividadDto {

    private Long idEmpleado;
    private Long idActividad;
    private Integer idTipoActividad;
    private Object idProyecto;      // Long cuando hay match, "N/A" cuando no
    private String descripcion;
    private String fechaRegistro;   // yyyy-MM-dd
    private String horaInicio;      // HH:mm
    private String horaFin;         // HH:mm
    private String fase;            // código de Fase, solo cuando idTipoActividad = Servicio
}
