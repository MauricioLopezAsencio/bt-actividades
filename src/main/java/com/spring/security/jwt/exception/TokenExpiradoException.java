package com.spring.security.jwt.exception;

public class TokenExpiradoException extends RuntimeException {

    public TokenExpiradoException(String nombreToken) {
        super("El token '" + nombreToken + "' ha caducado o es inválido");
    }
}
