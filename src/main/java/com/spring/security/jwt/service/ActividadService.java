package com.spring.security.jwt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.security.jwt.dto.ActividadDto;
import com.spring.security.jwt.dto.ActividadRequest;
import com.spring.security.jwt.dto.ActividadResultDto;
import com.spring.security.jwt.dto.CalendarioEventoDto;
import com.spring.security.jwt.dto.Fase;
import com.spring.security.jwt.dto.FaseDto;
import com.spring.security.jwt.dto.ProyectoDto;
import com.spring.security.jwt.exception.TokenExpiradoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ActividadService implements IActividadService {

    private static final DateTimeFormatter TIME_PARSE = DateTimeFormatter.ofPattern("HH:mm[:ss]");
    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm");

    // ─── IDs de actividad — ajustar según catálogo de bitácora ──────────────
    private static final long ID_SESION_INTERNA = 1L;
    private static final long ID_SESION_EXTERNA  = 2L;

    private static final int  ID_TIPO_ACTIVIDAD  = 3;
    private static final String NA               = "N/A";

    private final ICalendarioService     calendarioService;
    private final IBitacoraService       bitacoraService;
    private final ObjectMapper           objectMapper;
    private final MapeoTipoActividadFase mapeoFase;

    @Value("${app.bitacora.tipo-actividad-servicio-id:4}")
    private int idTipoActividadServicio;

    public ActividadService(ICalendarioService calendarioService,
                            IBitacoraService bitacoraService,
                            ObjectMapper objectMapper,
                            MapeoTipoActividadFase mapeoFase) {
        this.calendarioService = calendarioService;
        this.bitacoraService   = bitacoraService;
        this.objectMapper      = objectMapper;
        this.mapeoFase         = mapeoFase;
    }

    @Override
    public ActividadResultDto obtenerActividades(ActividadRequest request) {
        Long idEmpleado = bitacoraService.obtenerIdEmpleado(request.getUsername(), request.getPassword());
        List<CalendarioEventoDto> eventos = obtenerEventos(request);
        List<Map<String, Object>> proyectos = obtenerProyectos(idEmpleado,
                request.getUsername(), request.getPassword());
        Object tiposActividad = obtenerTiposActividad(request.getUsername(), request.getPassword());

        Map<String, List<Map<String, Object>>> registrosPorFecha =
                obtenerRegistrosPorFecha(idEmpleado, eventos, request.getUsername(), request.getPassword());

        List<ActividadDto> todas = eventos.stream()
                .flatMap(evento -> expandirEnFranjas(evento, idEmpleado, proyectos, registrosPorFecha).stream())
                .toList();

        Map<Boolean, List<ActividadDto>> particion = todas.stream()
                .collect(Collectors.partitioningBy(a -> !NA.equals(a.getIdProyecto())));

        return ActividadResultDto.builder()
                .actividades(particion.get(true))
                .sesionesNoPareadasAProyecto(particion.get(false))
                .proyectosDisponibles(mapearProyectos(proyectos))
                .tiposActividad(tiposActividad)
                .fases(Fase.ordenadas().stream().map(FaseDto::from).toList())
                .mapeoFases(mapeoFase.obtenerMapeo().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, e -> e.getValue().getCodigo())))
                .idTipoActividadServicio(idTipoActividadServicio)
                .build();
    }

    // ─── Registros existentes agrupados por fecha ────────────────────────────

    private Map<String, List<Map<String, Object>>> obtenerRegistrosPorFecha(
            Long idEmpleado, List<CalendarioEventoDto> eventos, String username, String password) {

        Set<String> fechas = eventos.stream()
                .map(e -> convertirFecha(e.getStart().split(" ")[0]))
                .collect(Collectors.toSet());

        Map<String, List<Map<String, Object>>> resultado = new java.util.HashMap<>();
        for (String fecha : fechas) {
            try {
                resultado.put(fecha,
                        bitacoraService.obtenerRegistrosPorEmpleadoYFecha(idEmpleado, fecha, username, password));
            } catch (Exception ex) {
                log.warn("No se pudieron obtener registros SCO fecha={}: {}", fecha, ex.getMessage());
                resultado.put(fecha, Collections.emptyList());
            }
        }
        return resultado;
    }

    // ─── Expande un evento en sus franjas libres respecto a Scoca ───────────

    private List<ActividadDto> expandirEnFranjas(CalendarioEventoDto evento, Long idEmpleado,
                                                  List<Map<String, Object>> proyectos,
                                                  Map<String, List<Map<String, Object>>> registrosPorFecha) {
        String[] startParts = evento.getStart().split(" ");
        String[] endParts   = evento.getEnd().split(" ");
        String fecha        = convertirFecha(startParts[0]);
        String horaInicio   = startParts[1];
        String horaFin      = endParts[1];

        Object idProyecto = findProyecto(evento.getSubject(), proyectos);
        List<Map<String, Object>> registrosDelDia = registrosPorFecha.getOrDefault(fecha, Collections.emptyList());

        // Tanto sesiones (NA) como actividades con proyecto usan los horarios libres reales de Scoca.
        // Si el slot queda completamente cubierto, se omite; los solapamientos parciales generan splits.
        List<String[]> franjas = calcularFranjasLibres(horaInicio, horaFin, registrosDelDia);

        if (franjas.isEmpty()) {
            log.debug("Evento completamente cubierto en Scoca, se omite subject='{}' fecha={} horario={}-{}",
                    evento.getSubject(), fecha, horaInicio, horaFin);
            return Collections.emptyList();
        }

        return franjas.stream()
                .map(f -> buildActividadDto(idEmpleado, evento, idProyecto, fecha, f[0], f[1]))
                .toList();
    }

    // ─── Calcula intervalos del evento no cubiertos por registros Scoca ──────

    private List<String[]> calcularFranjasLibres(String horaInicioStr, String horaFinStr,
                                                   List<Map<String, Object>> registros) {
        LocalTime inicio = LocalTime.parse(horaInicioStr, TIME_PARSE);
        LocalTime fin    = LocalTime.parse(horaFinStr,    TIME_PARSE);

        List<LocalTime[]> ocupados = registros.stream()
                .filter(r -> r.get("horaInicio") != null && r.get("horaFin") != null)
                .map(r -> new LocalTime[]{
                        parsearHora(normalizarHora(r.get("horaInicio"))),
                        parsearHora(normalizarHora(r.get("horaFin")))
                })
                .filter(o -> o[0] != null && o[1] != null)
                // >= en el extremo derecho: bloques que terminan exactamente en horaInicio del evento
                // también se incluyen, garantizando que el cursor los "vea" y no arranque en su borde.
                .filter(o -> o[0].isBefore(fin) && !o[1].isBefore(inicio))
                .sorted(java.util.Comparator.comparing(o -> o[0]))
                .toList();

        if (ocupados.isEmpty()) {
            return Collections.singletonList(new String[]{horaInicioStr, horaFinStr});
        }

        List<String[]> franjas = new ArrayList<>();
        LocalTime cursor = inicio;

        for (LocalTime[] ocupado : ocupados) {
            if (ocupado[0].isAfter(cursor)) {
                franjas.add(new String[]{cursor.format(TIME_FMT), ocupado[0].format(TIME_FMT)});
            }
            // >= en avance de cursor: si un bloque termina exactamente donde está cursor,
            // se recorre al siguiente punto disponible (evita que horaInicio choque con horaFin de Scoca).
            if (!ocupado[1].isBefore(cursor)) {
                cursor = ocupado[1];
            }
            if (!cursor.isBefore(fin)) break;
        }

        if (cursor.isBefore(fin)) {
            franjas.add(new String[]{cursor.format(TIME_FMT), fin.format(TIME_FMT)});
        }

        return franjas;
    }

    private ActividadDto buildActividadDto(Long idEmpleado, CalendarioEventoDto evento,
                                            Object idProyecto, String fecha,
                                            String horaInicio, String horaFin) {
        long idActividad = resolverIdActividad(evento.getModalidad());
        return ActividadDto.builder()
                .idEmpleado(idEmpleado)
                .idActividad(idActividad)
                .idTipoActividad(ID_TIPO_ACTIVIDAD)
                .idProyecto(idProyecto)
                .descripcion(evento.getSubject())
                .fechaRegistro(fecha)
                .horaInicio(horaInicio)
                .horaFin(horaFin)
                .fase(resolverFase(ID_TIPO_ACTIVIDAD, nombreActividadDeSesion(idActividad)))
                .build();
    }

    private String nombreActividadDeSesion(long idActividad) {
        if (idActividad == ID_SESION_INTERNA) return "Sesión interna";
        if (idActividad == ID_SESION_EXTERNA) return "Sesión externa";
        return null;
    }

    private String resolverFase(int idTipoActividad, String nombreActividad) {
        if (idTipoActividad != idTipoActividadServicio) return null;
        Fase fase = mapeoFase.resolver(nombreActividad);
        return fase != null ? fase.getCodigo() : null;
    }

    private LocalTime parsearHora(String hora) {
        try {
            return LocalTime.parse(hora, TIME_PARSE);
        } catch (Exception ex) {
            log.warn("No se pudo parsear hora '{}'", hora);
            return null;
        }
    }

    private String normalizarHora(Object hora) {
        if (hora == null) return "";
        String s = hora.toString();
        return s.length() > 5 ? s.substring(0, 5) : s;
    }

    // ─── Tipos de actividad ──────────────────────────────────────────────────

    private Object obtenerTiposActividad(String username, String password) {
        try {
            return bitacoraService.obtenerTiposActividad(username, password);
        } catch (Exception ex) {
            log.error("Error al obtener tipos de actividad: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Mapeo de proyectos para combo ──────────────────────────────────────

    private List<ProyectoDto> mapearProyectos(List<Map<String, Object>> proyectos) {
        if (proyectos == null) return Collections.emptyList();
        return proyectos.stream()
                .filter(p -> p.get("id") != null && p.get("descripcion") != null)
                .map(p -> ProyectoDto.builder()
                        .id(((Number) p.get("id")).longValue())
                        .descripcion(p.get("descripcion").toString())
                        .build())
                .toList();
    }

    // ─── idActividad según modalidad ─────────────────────────────────────────

    private long resolverIdActividad(String modalidad) {
        return "externa".equalsIgnoreCase(modalidad) ? ID_SESION_EXTERNA : ID_SESION_INTERNA;
    }

    // ─── Match de proyecto ───────────────────────────────────────────────────

    private Object findProyecto(String subject, List<Map<String, Object>> proyectos) {
        if (proyectos == null || proyectos.isEmpty() || subject == null) return NA;

        List<String> keywords = extraerKeywords(subject);
        if (keywords.isEmpty()) return NA;

        return proyectos.stream()
                .filter(p -> keywordsMatchenProyecto(keywords, p))
                .map(p -> {
                    Object id = p.get("id");
                    return id != null ? ((Number) id).longValue() : (Object) NA;
                })
                .findFirst()
                .orElse(NA);
    }

    /**
     * Divide el subject por delimitadores y espacios para obtener palabras individuales.
     * "Sesión Capacitación Moneki- Spot" → ["sesión", "capacitación", "moneki", "spot"]
     * "Valia | Daily"                    → ["valia", "daily"]
     */
    private List<String> extraerKeywords(String subject) {
        return List.of(subject.split("[|\\-:\\s]+")).stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(k -> k.length() > 2)
                .toList();
    }

    private boolean keywordsMatchenProyecto(List<String> keywords, Map<String, Object> proyecto) {
        Object descripcionRaw = proyecto.get("descripcion");
        if (descripcionRaw == null) return false;

        String descripcion = descripcionRaw.toString();
        int primerGuion = descripcion.indexOf('-');
        if (primerGuion < 0) return false;

        String fragmento = descripcion.substring(primerGuion + 1).toLowerCase();

        return keywords.stream().anyMatch(fragmento::contains);
    }

    // ─── Fecha "dd/MM/yyyy" → "yyyy-MM-dd" ──────────────────────────────────

    private String convertirFecha(String fecha) {
        String[] partes = fecha.split("/");
        return partes[2] + "-" + partes[1] + "-" + partes[0];
    }

    // ─── Llamadas a servicios ────────────────────────────────────────────────

    private List<CalendarioEventoDto> obtenerEventos(ActividadRequest request) {
        try {
            return calendarioService.obtenerEventos(
                    request.getTokenMicrosoft(), request.getFechaInicio(), request.getFechaFin());
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new TokenExpiradoException("tokenMicrosoft");
            }
            log.error("Error al obtener eventos de calendario: {}", ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Error al obtener eventos de calendario: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> obtenerProyectos(Long idEmpleado, String username, String password) {
        try {
            Object raw = bitacoraService.obtenerProyectosPorEmpleado(idEmpleado, username, password);
            if (raw == null) return Collections.emptyList();

            // El API devuelve { "status": "OK", "data": [...] }
            Map<String, Object> wrapper = objectMapper.convertValue(raw,
                    new TypeReference<Map<String, Object>>() {});
            Object data = wrapper.get("data");
            if (data == null) return Collections.emptyList();

            return objectMapper.convertValue(data,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new TokenExpiradoException("bitacora");
            }
            log.error("Error al obtener proyectos de bitácora idEmpleado={}: {}", idEmpleado, ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.error("Error al obtener proyectos de bitácora idEmpleado={}: {}", idEmpleado, ex.getMessage());
            return Collections.emptyList();
        }
    }
}
