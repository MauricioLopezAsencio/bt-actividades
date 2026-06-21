package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.CalendarioEventoDto;
import com.spring.security.jwt.dto.microsoft.GraphEvent;
import com.spring.security.jwt.dto.microsoft.GraphEventsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class CalendarioService implements ICalendarioService {

    private static final String GRAPH_CALENDAR_VIEW_URL =
            "https://graph.microsoft.com/v1.0/me/calendarView" +
            "?startDateTime=%s&endDateTime=%s" +
            "&$select=subject,start,end,type,recurrence,attendees" +
            "&$top=999";

    private static final String DOMINIO_INTERNO = "@casystem.com.mx";

    private static final ZoneId ZONA_MEXICO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final RestTemplate restTemplate;
    private final FeriadosMexicoService feriadosService;

    public CalendarioService(RestTemplate restTemplate, FeriadosMexicoService feriadosService) {
        this.restTemplate = restTemplate;
        this.feriadosService = feriadosService;
    }

    @Override
    public List<CalendarioEventoDto> obtenerEventos(String bearerToken, LocalDate fechaInicio, LocalDate fechaFin) {
        LocalDate desde = fechaInicio != null ? fechaInicio : LocalDate.of(2000, 1, 1);
        LocalDate hasta = fechaFin   != null ? fechaFin   : LocalDate.now(ZONA_MEXICO);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // Anclar a medianoche Mexico City con offset explícito (-06:00 / -05:00 según DST).
        // Sin offset, Graph interpreta la hora como UTC y devuelve eventos del día anterior.
        String startIso = desde.atStartOfDay(ZONA_MEXICO)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String endIso   = hasta.atTime(23, 59, 59)
                .atZone(ZONA_MEXICO)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String url      = String.format(GRAPH_CALENDAR_VIEW_URL, startIso, endIso);

        try {
            List<GraphEvent> todos = new ArrayList<>();
            String nextUrl = url;

            while (nextUrl != null) {
                ResponseEntity<GraphEventsResponse> response = restTemplate.exchange(
                        nextUrl, HttpMethod.GET, entity, GraphEventsResponse.class);

                GraphEventsResponse body = response.getBody();
                if (body == null) break;

                List<GraphEvent> pagina = Optional.ofNullable(body.getValue())
                        .orElse(Collections.emptyList());
                todos.addAll(pagina);

                nextUrl = body.getNextLink();
                log.info("Página calendarView count={} hasNextPage={}", pagina.size(), nextUrl != null);
            }

            log.info("Eventos totales obtenidos de Microsoft Graph count={}", todos.size());

            return todos.stream()
                    .filter(e -> !"seriesMaster".equalsIgnoreCase(e.getType()))
                    .map(this::toDto)
                    // Filtrar feriados usando la fecha ya convertida a Mexico City (dd/MM/yyyy HH:mm)
                    // para no depender de la fecha UTC cruda que devuelve Graph.
                    .filter(dto -> !feriadosService.esFeriado(
                            LocalDate.parse(dto.getStart().substring(0, 10),
                                    DateTimeFormatter.ofPattern("dd/MM/yyyy"))))
                    .toList();

        } catch (HttpClientErrorException ex) {
            log.error("Error al consultar Microsoft Graph status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw ex;
        }
    }

    // ─── Conversión simple ───────────────────────────────────────────────────

    private CalendarioEventoDto toDto(GraphEvent event) {
        String startDate = null;
        String endDate = null;
        if (event.getRecurrence() != null && event.getRecurrence().getRange() != null) {
            startDate = event.getRecurrence().getRange().getStartDate();
            endDate   = event.getRecurrence().getRange().getEndDate();
        }
        return CalendarioEventoDto.builder()
                .subject(event.getSubject())
                .start(convertirAMexico(event.getStart().getDateTime(), event.getStart().getTimeZone()))
                .end(convertirAMexico(event.getEnd().getDateTime(), event.getEnd().getTimeZone()))
                .type(event.getType())
                .startDate(startDate)
                .endDate(endDate)
                .modalidad(resolverModalidad(event))
                .build();
    }

    // ─── Modalidad ──────────────────────────────────────────────────────────

    private String resolverModalidad(GraphEvent event) {
        if (event.getAttendees() == null || event.getAttendees().isEmpty()) {
            return "interna";
        }
        boolean todosInternos = event.getAttendees().stream()
                .filter(a -> a.getEmailAddress() != null && a.getEmailAddress().getAddress() != null)
                .allMatch(a -> a.getEmailAddress().getAddress()
                        .toLowerCase().endsWith(DOMINIO_INTERNO));
        return todosInternos ? "interna" : "externa";
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private String convertirAMexico(String dateTime, String sourceTimeZone) {
        ZoneId origen    = resolverZona(sourceTimeZone);
        String truncated = dateTime.length() > 19 ? dateTime.substring(0, 19) : dateTime;
        LocalDateTime local       = LocalDateTime.parse(truncated, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ZonedDateTime enMexico    = ZonedDateTime.of(local, origen).withZoneSameInstant(ZONA_MEXICO);
        return enMexico.format(FORMATTER);
    }

    private ZoneId resolverZona(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (Exception ex) {
            log.warn("Zona horaria no reconocida '{}', se usa UTC", timeZone);
            return ZoneId.of("UTC");
        }
    }
}
