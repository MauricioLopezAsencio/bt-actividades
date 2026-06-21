package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ActividadResultDto {

    private List<ActividadDto>  actividades;
    private List<ActividadDto>  sesionesNoPareadasAProyecto;
    private List<ProyectoDto>   proyectosDisponibles;
    private Object              tiposActividad;
    private List<FaseDto>       fases;
    private Map<String, String> mapeoFases;
    private Integer             idTipoActividadServicio;
}
