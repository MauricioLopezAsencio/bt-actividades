package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProyectoDto {

    private Long id;
    private String descripcion;
}
