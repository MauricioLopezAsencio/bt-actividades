package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EstadisticasMesDto {

    private String nombreMes;
    private int    mes;
    private int    anio;
    private int    diasHabiles;
    private int    diasConRegistro;
    private double horasEsperadas;
    private double horasRegistradas;
    private double porcentaje;
}
