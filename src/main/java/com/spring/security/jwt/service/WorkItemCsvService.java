package com.spring.security.jwt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.security.jwt.dto.ActividadDto;
import com.spring.security.jwt.dto.Fase;
import com.spring.security.jwt.dto.WorkItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkItemCsvService {

    // Fallbacks si el catálogo no responde o no matchea por nombre
    private static final long ID_ACTIVIDAD_FALLBACK  = 3L;
    private static final int  ID_TIPO_ACTIVIDAD_FALLBACK = 3;

    private static final LocalTime INICIO_DIA = LocalTime.of(9, 0);
    private static final LocalTime FIN_DIA    = LocalTime.of(18, 0);

    private static final DateTimeFormatter TIME_FMT   = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_PARSE = DateTimeFormatter.ofPattern("HH:mm[:ss]");

    private static final String SEP_TAB   = "\t";
    private static final String SEP_COMMA = ",";

    private static final int COL_TYPE      = 0;
    private static final int COL_STATE     = 1;
    private static final int COL_ID        = 2;
    private static final int COL_TITLE     = 3;
    private static final int COL_ITERATION = 4;
    private static final int COL_MINUTOS   = 5;

    private final IBitacoraService       bitacoraService;
    private final ObjectMapper           objectMapper;
    private final MapeoTipoActividadFase mapeoFase;

    @Value("${app.bitacora.tipo-actividad-servicio-id:4}")
    private int idTipoActividadServicio;

    public WorkItemCsvService(IBitacoraService bitacoraService,
                              ObjectMapper objectMapper,
                              MapeoTipoActividadFase mapeoFase) {
        this.bitacoraService = bitacoraService;
        this.objectMapper    = objectMapper;
        this.mapeoFase       = mapeoFase;
    }

    // ── Parseo puro del CSV ───────────────────────────────────────────────────

    public List<WorkItemDto> parsear(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<WorkItemDto> items = new ArrayList<>();
            String line;
            String separator = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (separator == null) {
                    separator = line.contains(SEP_TAB) ? SEP_TAB : SEP_COMMA;
                    log.info("Separador detectado: '{}'", separator.equals(SEP_TAB) ? "TAB" : "COMMA");
                    continue;
                }

                WorkItemDto item = parsearLinea(line, separator);
                if (item != null) items.add(item);
            }

            log.info("Work items parseados del CSV: {}", items.size());
            return items;

        } catch (Exception ex) {
            log.error("Error al parsear CSV de work items: {}", ex.getMessage());
            throw new RuntimeException("Error al procesar el archivo CSV: " + ex.getMessage(), ex);
        }
    }

    // ── Preparar actividades listas para registro ─────────────────────────────

    public List<ActividadDto> preparar(MultipartFile file, String fecha,
                                       String username, String password) {

        List<WorkItemDto> items = parsear(file);

        List<WorkItemDto> conMinutos = items.stream()
                .filter(i -> i.getMinutosWorked() != null && i.getMinutosWorked() > 0)
                .toList();

        if (conMinutos.isEmpty()) {
            log.warn("No hay work items con minutos registrados en el CSV");
            return Collections.emptyList();
        }

        Long idEmpleado = bitacoraService.obtenerIdEmpleado(username, password);
        List<Map<String, Object>> proyectos = obtenerProyectos(idEmpleado, username, password);

        Integer idTipoServicio = resolverIdTipoActividad("servicio", username, password);
        Long    idDesarrollo   = resolverIdActividad("desarrollo", idTipoServicio, username, password);

        LocalDate fechaActual = (fecha != null && !fecha.isBlank())
                ? LocalDate.parse(fecha)
                : LocalDate.now();

        List<LocalTime[]> ocupados = cargarOcupados(idEmpleado, fechaActual, username, password);
        LocalTime cursor           = INICIO_DIA;
        List<ActividadDto> resultado = new ArrayList<>();

        for (WorkItemDto item : conMinutos) {
            LocalTime[] slot = encontrarSlot(cursor, item.getMinutosWorked(), ocupados);

            // Si no cabe en el día actual → siguiente día hábil
            if (slot == null) {
                fechaActual = siguienteDiaHabil(fechaActual);
                ocupados    = cargarOcupados(idEmpleado, fechaActual, username, password);
                cursor      = INICIO_DIA;
                slot        = encontrarSlot(cursor, item.getMinutosWorked(), ocupados);
            }

            if (slot == null) {
                log.warn("Sin espacio para work item id={} '{}' incluso en día siguiente", item.getId(), item.getTitle());
                continue;
            }

            Object idProyecto = matchProyecto(item.getIterationPath(), proyectos);
            String faseCodigo = resolverFase(idTipoServicio, "Desarrollo");

            resultado.add(ActividadDto.builder()
                    .idEmpleado(idEmpleado)
                    .idActividad(idDesarrollo)
                    .idTipoActividad(idTipoServicio)
                    .idProyecto(idProyecto)
                    .descripcion(item.getTitle())
                    .fechaRegistro(fechaActual.toString())
                    .horaInicio(slot[0].format(TIME_FMT))
                    .horaFin(slot[1].format(TIME_FMT))
                    .fase(faseCodigo)
                    .build());

            ocupados.add(slot);
            cursor = slot[1];
        }

        log.info("Work items preparados: {}/{} idEmpleado={} idTipo={} idActividad={}",
                resultado.size(), conMinutos.size(), idEmpleado, idTipoServicio, idDesarrollo);
        return resultado;
    }

    // ── Resolución de fase según mapeo ────────────────────────────────────────

    private String resolverFase(Integer idTipoActividad, String nombreActividad) {
        if (idTipoActividad == null || idTipoActividad != idTipoActividadServicio) {
            return null;
        }
        Fase fase = mapeoFase.resolver(nombreActividad);
        return fase != null ? fase.getCodigo() : null;
    }

    // ── Resolución dinámica de catálogo Scoca ─────────────────────────────────

    private Integer resolverIdTipoActividad(String nombreBuscado, String username, String password) {
        try {
            Object raw = bitacoraService.obtenerTiposActividad(username, password);
            log.info("RAW tipos actividad: {}", raw);
            List<Map<String, Object>> tipos = parsearListaGenerica(raw);
            log.info("Tipos parseados ({}): {}", tipos.size(), tipos);

            return tipos.stream()
                    .filter(t -> {
                        String nombre = resolverCampoNombre(t);
                        log.info("  tipo keys={} nombre='{}'", t.keySet(), nombre);
                        return nombre != null && nombre.toLowerCase().contains(nombreBuscado.toLowerCase());
                    })
                    .map(t -> resolverCampoId(t))
                    .filter(id -> id != null)
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("No se encontró tipo '{}' en catálogo, usando fallback {}",
                                nombreBuscado, ID_TIPO_ACTIVIDAD_FALLBACK);
                        return ID_TIPO_ACTIVIDAD_FALLBACK;
                    });
        } catch (Exception ex) {
            log.warn("Error al resolver idTipoActividad '{}': {}", nombreBuscado, ex.getMessage());
            return ID_TIPO_ACTIVIDAD_FALLBACK;
        }
    }

    private Long resolverIdActividad(String nombreBuscado, Integer idTipo,
                                      String username, String password) {
        try {
            Object raw = bitacoraService.obtenerActividadesPorTipo(idTipo, username, password);
            log.info("RAW actividades tipo {}: {}", idTipo, raw);
            List<Map<String, Object>> actividades = parsearListaGenerica(raw);
            log.info("Actividades parseadas ({}): {}", actividades.size(), actividades);

            return actividades.stream()
                    .filter(a -> {
                        String nombre = resolverCampoNombre(a);
                        log.info("  actividad keys={} nombre='{}'", a.keySet(), nombre);
                        return nombre != null && nombre.toLowerCase().contains(nombreBuscado.toLowerCase());
                    })
                    .map(a -> {
                        Integer id = resolverCampoId(a);
                        return id != null ? id.longValue() : null;
                    })
                    .filter(id -> id != null)
                    .findFirst()
                    .orElseGet(() -> {
                        log.warn("No se encontró actividad '{}' para tipo {}, usando fallback {}",
                                nombreBuscado, idTipo, ID_ACTIVIDAD_FALLBACK);
                        return ID_ACTIVIDAD_FALLBACK;
                    });
        } catch (Exception ex) {
            log.warn("Error al resolver idActividad '{}': {}", nombreBuscado, ex.getMessage());
            return ID_ACTIVIDAD_FALLBACK;
        }
    }

    /** Extrae el ID probando los nombres de campo más comunes */
    private Integer resolverCampoId(Map<String, Object> map) {
        for (String campo : List.of("id", "idTipoActividad", "idActividad", "ID", "codigo")) {
            Object val = map.get(campo);
            if (val instanceof Number n) return n.intValue();
        }
        log.warn("No se encontró campo ID en: {}", map.keySet());
        return null;
    }

    /** Extrae el nombre probando los nombres de campo más comunes */
    private String resolverCampoNombre(Map<String, Object> map) {
        for (String campo : List.of("nombre", "descripcion", "name", "dsActividad",
                                    "dsTipoActividad", "titulo", "label", "actividad", "tipo")) {
            Object val = map.get(campo);
            if (val != null) return val.toString();
        }
        return null;
    }

    // ── Gestión de días ───────────────────────────────────────────────────────

    private List<LocalTime[]> cargarOcupados(Long idEmpleado, LocalDate fecha,
                                              String username, String password) {
        List<Map<String, Object>> registros = bitacoraService.obtenerRegistrosPorEmpleadoYFecha(
                idEmpleado, fecha.toString(), username, password);
        log.info("Registros Scoca para {}: {}", fecha, registros.size());
        return construirOcupados(registros);
    }

    private LocalDate siguienteDiaHabil(LocalDate fecha) {
        LocalDate siguiente = fecha.plusDays(1);
        while (siguiente.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || siguiente.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            siguiente = siguiente.plusDays(1);
        }
        log.info("Desbordamiento al siguiente día hábil: {}", siguiente);
        return siguiente;
    }

    // ── Cálculo de slots libres ───────────────────────────────────────────────

    private List<LocalTime[]> construirOcupados(List<Map<String, Object>> registros) {
        return registros.stream()
                .filter(r -> r.get("horaInicio") != null && r.get("horaFin") != null)
                .map(r -> new LocalTime[]{
                        parsearHora(r.get("horaInicio").toString()),
                        parsearHora(r.get("horaFin").toString())
                })
                .filter(o -> o[0] != null && o[1] != null)
                .sorted(Comparator.comparing(o -> o[0]))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private LocalTime[] encontrarSlot(LocalTime desde, int duracionMin,
                                       List<LocalTime[]> ocupados) {
        LocalTime cursor = desde;

        while (!cursor.plusMinutes(duracionMin).isAfter(FIN_DIA)) {
            final LocalTime desde2       = cursor;
            final LocalTime candidatoFin = cursor.plusMinutes(duracionMin);

            Optional<LocalTime[]> conflicto = ocupados.stream()
                    .filter(o -> o[0].isBefore(candidatoFin) && o[1].isAfter(desde2))
                    .min(Comparator.comparing(o -> o[0]));

            if (conflicto.isEmpty()) {
                return new LocalTime[]{desde2, candidatoFin};
            }

            cursor = conflicto.get()[1];
        }

        return null;
    }

    // ── Match de proyecto por Iteration Path ─────────────────────────────────

    /**
     * Extrae el código de proyecto desde el Iteration Path de Azure DevOps.
     * Descarta el prefijo organizacional (todo antes del primer guion) y el sufijo de sprint.
     * "PGO-VALIA-001-2025\Sprint 10" → "valia-001-2025"
     * "PGO-MONEKI-2025"              → "moneki-2025"
     */
    private String extraerCodigoDesdeIterationPath(String iterationPath) {
        // Quitar sufijo \Sprint N (y cualquier cosa después de \)
        String base = iterationPath.split("\\\\")[0].trim();
        // Tomar todo lo que está después del primer guion (saltar prefijo "PGO")
        int primerGuion = base.indexOf('-');
        if (primerGuion < 0) return base.toLowerCase();
        return base.substring(primerGuion + 1).toLowerCase();
    }

    private Object matchProyecto(String iterationPath, List<Map<String, Object>> proyectos) {
        if (iterationPath == null || proyectos == null || proyectos.isEmpty()) return "N/A";

        String codigo = extraerCodigoDesdeIterationPath(iterationPath);
        log.info("Match proyecto iterationPath='{}' codigo='{}'", iterationPath, codigo);
        proyectos.forEach(p -> log.info("  proyecto disponible: id={} descripcion='{}'",
                p.get("id"), p.get("descripcion")));

        return proyectos.stream()
                .filter(p -> {
                    Object descRaw = p.get("descripcion");
                    if (descRaw == null) return false;
                    boolean hit = descRaw.toString().toLowerCase().contains(codigo);
                    log.info("  id={} desc='{}' hit={}", p.get("id"), descRaw, hit);
                    return hit;
                })
                .map(p -> {
                    Object id = p.get("id");
                    log.info("  → MATCH id={} descripcion='{}'", id, p.get("descripcion"));
                    return id != null ? ((Number) id).longValue() : (Object) "N/A";
                })
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Sin match de proyecto para iterationPath='{}' codigo='{}'", iterationPath, codigo);
                    return "N/A";
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsearListaGenerica(Object raw) {
        if (raw == null) return Collections.emptyList();
        if (raw instanceof List<?> list) return (List<Map<String, Object>>) list;
        if (raw instanceof Map<?, ?> map) {
            Object data = map.get("data");
            if (data instanceof List<?> list) return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> obtenerProyectos(Long idEmpleado,
                                                        String username, String password) {
        try {
            Object raw = bitacoraService.obtenerProyectosPorEmpleado(idEmpleado, username, password);
            if (raw == null) return Collections.emptyList();
            Map<String, Object> wrapper = objectMapper.convertValue(raw,
                    new TypeReference<Map<String, Object>>() {});
            Object data = wrapper.get("data");
            if (data == null) return Collections.emptyList();
            return objectMapper.convertValue(data,
                    new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            log.error("Error al obtener proyectos de Scoca: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private LocalTime parsearHora(String hora) {
        try {
            String h = hora.length() > 5 ? hora.substring(0, 5) : hora;
            return LocalTime.parse(h, TIME_PARSE);
        } catch (Exception ex) {
            log.warn("No se pudo parsear hora '{}'", hora);
            return null;
        }
    }

    private WorkItemDto parsearLinea(String line, String separator) {
        String[] cols = line.split(separator, -1);

        if (cols.length < COL_ITERATION + 1) {
            log.warn("Línea ignorada por columnas insuficientes: {}", line);
            return null;
        }

        try {
            String  type      = cols[COL_TYPE].trim();
            String  state     = cols[COL_STATE].trim();
            String  idRaw     = cols[COL_ID].trim();
            String  title     = cols[COL_TITLE].trim();
            String  iteration = cols[COL_ITERATION].trim();
            Integer minutos   = null;

            if (cols.length > COL_MINUTOS) {
                String raw = cols[COL_MINUTOS].trim();
                if (!raw.isEmpty()) minutos = Integer.parseInt(raw);
            }

            return WorkItemDto.builder()
                    .id(Integer.parseInt(idRaw))
                    .workItemType(type)
                    .state(state)
                    .title(title)
                    .iterationPath(iteration)
                    .minutosWorked(minutos)
                    .build();

        } catch (NumberFormatException ex) {
            log.warn("Línea ignorada por error de formato numérico: {}", line);
            return null;
        }
    }
}
