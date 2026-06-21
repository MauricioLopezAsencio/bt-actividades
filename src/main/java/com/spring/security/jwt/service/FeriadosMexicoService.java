package com.spring.security.jwt.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Días feriados oficiales + empresa.
 * Formato MM-dd para que aplique independientemente del año,
 * excepto Semana Santa que es movible y debe revisarse anualmente.
 */
@Service
public class FeriadosMexicoService {

    private static final Set<String> FERIADOS = Set.of(
            "01-01",  // Año Nuevo
            "02-02",  // Constitución (recorrido)
            "03-16",  // Benito Juárez (recorrido)
            "04-02",  // Semana Santa (empresa)
            "04-03",  // Semana Santa (empresa)
            "05-01",  // Día del Trabajo
            "09-16",  // Independencia
            "11-16",  // Revolución (recorrido)
            "12-25"   // Navidad
    );

    public boolean esFeriado(LocalDate fecha) {
        String mesdia = String.format("%02d-%02d", fecha.getMonthValue(), fecha.getDayOfMonth());
        return FERIADOS.contains(mesdia);
    }
}
