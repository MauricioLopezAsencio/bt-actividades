package com.spring.security.jwt.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.spring.security.jwt.dto.LoginResponseDto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BitacoraTokenManager {

    private static final String LOGIN_URL = "https://scoca.casystem.com.mx/api/auth/login";
    private static final long TTL_SEGUNDOS = 23L * 60 * 60;

    private final RestTemplate restTemplate;

    /** Caché por username: evita re-login en cada petición. */
    private final ConcurrentHashMap<String, TokenEntry> cache = new ConcurrentHashMap<>();

    public BitacoraTokenManager(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Autentica las credenciales contra el SCO con un login fresco y
     * devuelve el token + idEmpleado. También calienta la caché para
     * que las siguientes peticiones no repitan el login.
     */
    public LoginResponseDto autenticar(String username, String password) {
        TokenEntry entry = login(username, password);
        cache.put(username, entry);
        return LoginResponseDto.builder()
                .token(entry.token)
                .idEmpleado(entry.idEmpleado)
                .build();
    }

    /** Devuelve token vigente; hace login si expiró o no existe. */
    public String obtenerToken(String username, String password) {
        return obtenerEntry(username, password).token;
    }

    /** Devuelve el idEmpleado extraído de la respuesta del login del SCO. */
    public Long obtenerIdEmpleado(String username, String password) {
        return obtenerEntry(username, password).idEmpleado;
    }

    /** Fuerza renovación (llamar cuando la API responda 401). */
    public String renovarToken(String username, String password) {
        log.info("Renovando token por 401 username={}", username);
        TokenEntry entry = login(username, password);
        cache.put(username, entry);
        return entry.token;
    }

    private TokenEntry obtenerEntry(String username, String password) {
        TokenEntry entry = cache.get(username);
        if (entry == null || entry.estaExpirado()) {
            log.info("Token no vigente para username={}, solicitando nuevo...", username);
            entry = login(username, password);
            cache.put(username, entry);
        }
        return entry;
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private TokenEntry login(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "username", username,
                "password", md5(password)   // el servidor espera MD5
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    LOGIN_URL, new HttpEntity<>(body, headers), Map.class);

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Respuesta vacía del login de bitácora");
            }

            Object data = responseBody.get("data");
            if (data instanceof Map<?, ?> dataMap && dataMap.get("token") != null) {
                log.info("Token obtenido correctamente username={}", username);
                String token      = dataMap.get("token").toString();
                Long   idEmpleado = extraerIdEmpleado(dataMap);
                return new TokenEntry(token, idEmpleado);
            }
            Object token = responseBody.get("token");
            if (token != null) {
                log.info("Token obtenido correctamente username={}", username);
                Long idEmpleado = extraerIdEmpleado(responseBody);
                return new TokenEntry(token.toString(), idEmpleado);
            }
            throw new RuntimeException("No se encontró el token en la respuesta de bitácora");

        } catch (HttpStatusCodeException ex) {
            // Propaga el status real del SCO (p. ej. 401 credenciales inválidas)
            log.warn("SCO respondió {} en login username={}", ex.getStatusCode(), username);
            throw ex;
        } catch (Exception ex) {
            log.error("Error al autenticar en bitácora username={}: {}", username, ex.getMessage());
            throw new RuntimeException("Error al autenticar en bitácora: " + ex.getMessage(), ex);
        }
    }

    private Long extraerIdEmpleado(Map<?, ?> map) {
        Object raw = map.get("id");
        if (raw == null) return null;
        return ((Number) raw).longValue();
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 no disponible", e);
        }
    }

    // ── Clase interna de caché ────────────────────────────────────────────────

    private static class TokenEntry {
        final String token;
        final Long   idEmpleado;
        final Instant expiracion;

        TokenEntry(String token, Long idEmpleado) {
            this.token      = token;
            this.idEmpleado = idEmpleado;
            this.expiracion = Instant.now().plusSeconds(TTL_SEGUNDOS);
        }

        boolean estaExpirado() {
            return Instant.now().isAfter(expiracion);
        }
    }
}
