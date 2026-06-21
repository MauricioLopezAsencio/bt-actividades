package com.spring.security.jwt.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class BitacoraDto {
    private Long id;
    private String nombreEmpleado;
    private String nombreHerramienta;
    private boolean estatus;
    private LocalDate fecha;
    private String turno;

}
