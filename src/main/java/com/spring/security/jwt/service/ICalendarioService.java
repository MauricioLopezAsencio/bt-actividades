package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.CalendarioEventoDto;

import java.time.LocalDate;
import java.util.List;

public interface ICalendarioService {

    List<CalendarioEventoDto> obtenerEventos(String bearerToken, LocalDate fechaInicio, LocalDate fechaFin);

    default List<CalendarioEventoDto> obtenerEventos(String bearerToken) {
        return obtenerEventos(bearerToken, null, null);
    }
}
