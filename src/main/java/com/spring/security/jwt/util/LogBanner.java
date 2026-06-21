package com.spring.security.jwt.util;

import org.slf4j.Logger;

/**
 * Marcos de log para enmarcar procesos y leerlos fácil en consola. Ejemplo:
 *
 * <pre>
 * =================================================
 * INICIO - Proceso de generación de reporte
 * =================================================
 *   ... logs del proceso ...
 * =================================================
 * FIN - Proceso de generación de reporte (1234 ms)
 * =================================================
 * </pre>
 *
 * Uso típico:
 * <pre>
 * long t0 = LogBanner.inicio(log, "Proceso de generación de reporte");
 * // ... trabajo ...
 * LogBanner.fin(log, "Proceso de generación de reporte", t0);
 * </pre>
 */
public final class LogBanner {

    private static final String LINEA = "=".repeat(49);

    private LogBanner() {
    }

    /** Marca el arranque de un proceso y devuelve el instante inicial (ms) para medir duración. */
    public static long inicio(Logger log, String titulo) {
        log.info(LINEA);
        log.info("INICIO - {}", titulo);
        log.info(LINEA);
        return System.currentTimeMillis();
    }

    /** Marca el fin de un proceso, mostrando la duración desde el {@code inicioMs} devuelto por {@link #inicio}. */
    public static void fin(Logger log, String titulo, long inicioMs) {
        long ms = System.currentTimeMillis() - inicioMs;
        log.info(LINEA);
        log.info("FIN - {} ({} ms)", titulo, ms);
        log.info(LINEA);
    }

    /** Separador de sección intermedio dentro de un proceso. */
    public static void seccion(Logger log, String titulo) {
        log.info("----- {} -----", titulo);
    }
}
