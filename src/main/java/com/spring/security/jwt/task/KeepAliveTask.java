package com.spring.security.jwt.task;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Self-ping para mantener despierta la instancia free de Render.
 *
 * <p>Render duerme el servicio tras ~15 min sin tráfico HTTP entrante. Esta tarea
 * hace una petición a la propia URL pública del servicio cada pocos minutos, lo que
 * cuenta como tráfico entrante y evita el spin-down. Cada ping queda registrado en
 * los logs para poder verlo desde el dashboard de Render.</p>
 *
 * <p>La URL base se toma de {@code RENDER_EXTERNAL_URL}, que Render inyecta
 * automáticamente (p. ej. {@code https://bt-actividades.onrender.com}). En local esa
 * variable no existe, así que la tarea se autodesactiva.</p>
 *
 * <p><b>Límite conocido:</b> el self-ping solo mantiene vivo el servicio mientras ya
 * está corriendo; no puede despertarlo desde cero. Si llegara a dormirse (deploy,
 * hueco entre pings), necesita una petición externa para volver a arrancar.</p>
 */
@Component
public class KeepAliveTask {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveTask.class);

    @Value("${app.keep-alive.enabled:true}")
    private boolean enabled;

    /** Inyectada por Render como RENDER_EXTERNAL_URL; vacía en local. */
    @Value("${app.keep-alive.base-url:${RENDER_EXTERNAL_URL:}}")
    private String baseUrl;

    @Value("${app.keep-alive.path:/api/v1/actividades/fases}")
    private String path;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @PostConstruct
    void logEstado() {
        if (!enabled) {
            log.info("[keep-alive] desactivado (app.keep-alive.enabled=false)");
        } else if (!StringUtils.hasText(baseUrl)) {
            log.info("[keep-alive] desactivado: no hay RENDER_EXTERNAL_URL (entorno local)");
        } else {
            log.info("[keep-alive] activo -> pingeando {}{}", baseUrl, path);
        }
    }

    /**
     * Ping cada 10 min SOLO en horario activo (6:00–medianoche, hora México), para no
     * gastar horas del plan free de madrugada: fuera de ese rango el servicio se duerme.
     * Cron de 6 campos (seg min hora día mes díaSemana). Configurable con
     * KEEP_ALIVE_CRON / KEEP_ALIVE_ZONE.
     */
    @Scheduled(
            cron = "${app.keep-alive.cron:0 0/10 6-23 * * *}",
            zone = "${app.keep-alive.zone:America/Mexico_City}")
    public void ping() {
        if (!enabled || !StringUtils.hasText(baseUrl)) {
            return;
        }

        String url = baseUrl + path;
        long inicio = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "bt-actividades-keep-alive")
                    .GET()
                    .build();

            HttpResponse<Void> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            long ms = System.currentTimeMillis() - inicio;
            log.info("[keep-alive] ping {} -> HTTP {} ({} ms)", url, response.statusCode(), ms);
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - inicio;
            log.warn("[keep-alive] ping {} falló tras {} ms: {}", url, ms, e.toString());
        }
    }
}
