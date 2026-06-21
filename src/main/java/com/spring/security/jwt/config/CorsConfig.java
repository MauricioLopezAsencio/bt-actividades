package com.spring.security.jwt.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuración CORS para el módulo de actividades.
 *
 * Este proyecto no usa Spring Security ni JWT: todas las rutas
 * (/api/v1/actividades, /api/v1/bitacora, /api/v1/calendario,
 *  /api/v1/estadisticas) son públicas. La autenticación contra Scoca
 * se realiza por usuario/contraseña en cada petición (ver BitacoraTokenManager).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // allowedOriginPatterns permite cualquier origen incluso con
                // allowCredentials(true); allowedOrigins("*") sería inválido en ese caso.
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Transaction-Id")
                .allowCredentials(true);
    }
}
