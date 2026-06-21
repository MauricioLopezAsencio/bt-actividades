package com.spring.security.jwt.dto;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public enum Fase {

    ANALISIS_DISENO(1, "Análisis y Diseño"),
    DESARROLLO_CONSTRUCCION(2, "Desarrollo y Construcción"),
    PRUEBAS(3, "Pruebas"),
    DESPLIEGUE(4, "Despliegue"),
    GARANTIA(5, "Garantía"),
    NO_APLICA(6, "No Aplica");

    private final int orden;
    private final String nombre;

    Fase(int orden, String nombre) {
        this.orden = orden;
        this.nombre = nombre;
    }

    public int getOrden() {
        return orden;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCodigo() {
        return name();
    }

    public static List<Fase> ordenadas() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(Fase::getOrden))
                .toList();
    }
}
