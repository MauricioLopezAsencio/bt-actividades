package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FaseDto {

    private final String codigo;
    private final String nombre;
    private final int orden;

    public static FaseDto from(Fase fase) {
        return FaseDto.builder()
                .codigo(fase.getCodigo())
                .nombre(fase.getNombre())
                .orden(fase.getOrden())
                .build();
    }
}
