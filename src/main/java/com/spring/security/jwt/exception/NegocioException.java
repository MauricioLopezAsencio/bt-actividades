package com.spring.security.jwt.exception;

/**
 * Excepción que representa una violación de regla de negocio.
 * Resulta en HTTP 409 Conflict — no es un error del servidor.
 */
public class NegocioException extends RuntimeException {

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
