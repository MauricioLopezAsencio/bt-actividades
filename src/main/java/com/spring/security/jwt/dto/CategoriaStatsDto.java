package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoriaStatsDto {

    private final String categoria;
    private final int totalUnidades;
    private final int prestadas;
    private final int disponibles;
}
