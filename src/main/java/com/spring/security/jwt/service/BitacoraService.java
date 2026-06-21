package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.RegistrarActividadRequest;
import com.spring.security.jwt.exception.NegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BitacoraService implements IBitacoraService {

    private static final String BITACORA_URL =
            "https://scoca.casystem.com.mx/api/bitacora/proyectos/byEmpleado/{idEmpleado}";

    private static final String REGISTRAR_ACTIVIDAD_URL =
            "https://scoca.casystem.com.mx/api/bitacora";

    private static final String TIPO_ACTIVIDAD_URL =
            "https://scoca.casystem.com.mx/api/bitacora/tipoActividad";

    private static final String ACTIVIDADES_POR_TIPO_URL =
            "https://scoca.casystem.com.mx/api/bitacora/actividades/{idTipoActividad}";

    private static final String REGISTROS_POR_EMPLEADO_FECHA_URL =
            "https://scoca.casystem.com.mx/api/bitacora/registrosByEmpleadoAndFechaRegistro/{idEmpleado}/{fecha}";

    private static final DateTimeFormatter TIME_FMT    = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_PARSE  = DateTimeFormatter.ofPattern("HH:mm[:ss]");

    private final RestTemplate restTemplate;
    private final BitacoraTokenManager tokenManager;

    @Value("${app.bitacora.tipo-actividad-servicio-id:4}")
    private int idTipoActividadServicio;

    public BitacoraService(RestTemplate restTemplate, BitacoraTokenManager tokenManager) {
        this.restTemplate = restTemplate;
        this.tokenManager = tokenManager;
    }

    @Override
    public Long obtenerIdEmpleado(String username, String password) {
        return tokenManager.obtenerIdEmpleado(username, password);
    }

    @Override
    public Object obtenerProyectosPorEmpleado(Long idEmpleado, String username, String password) {
        try {
            return ejecutarConsulta(idEmpleado, tokenManager.obtenerToken(username, password));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expirado, renovando y reintentando idEmpleado={}", idEmpleado);
                return ejecutarConsulta(idEmpleado, tokenManager.renovarToken(username, password));
            }
            log.error("Error al consultar bitácora idEmpleado={} status={}", idEmpleado, ex.getStatusCode());
            throw ex;
        }
    }

    @Override
    public Object registrarActividad(RegistrarActividadRequest request) {
        validarFaseSiAplica(request);

        Long idEmpleado = tokenManager.obtenerIdEmpleado(request.getUsername(), request.getPassword());
        String token    = tokenManager.obtenerToken(request.getUsername(), request.getPassword());
        try {
            return ejecutarRegistro(request, idEmpleado, token);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expirado al registrar actividad, renovando...");
                String nuevoToken = tokenManager.renovarToken(request.getUsername(), request.getPassword());
                return ejecutarRegistro(request, idEmpleado, nuevoToken);
            }
            log.error("Error al registrar actividad en bitácora status={}", ex.getStatusCode());
            throw ex;
        }
    }

    @Override
    public Object obtenerTiposActividad(String username, String password) {
        try {
            return ejecutarGetSimple(TIPO_ACTIVIDAD_URL, tokenManager.obtenerToken(username, password));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expirado al obtener tipos de actividad, renovando...");
                return ejecutarGetSimple(TIPO_ACTIVIDAD_URL, tokenManager.renovarToken(username, password));
            }
            log.error("Error al consultar tipos de actividad status={}", ex.getStatusCode());
            throw ex;
        }
    }

    @Override
    public Object obtenerActividadesPorTipo(Integer idTipoActividad, String username, String password) {
        try {
            return ejecutarGetConVariable(ACTIVIDADES_POR_TIPO_URL,
                    tokenManager.obtenerToken(username, password), idTipoActividad);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expirado al obtener actividades idTipoActividad={}, renovando...", idTipoActividad);
                return ejecutarGetConVariable(ACTIVIDADES_POR_TIPO_URL,
                        tokenManager.renovarToken(username, password), idTipoActividad);
            }
            log.error("Error al consultar actividades idTipoActividad={} status={}", idTipoActividad, ex.getStatusCode());
            throw ex;
        }
    }

    // ─── Registro con partición anti-traslape ────────────────────────────────

    @Override
    public List<Object> registrarActividadConParticion(RegistrarActividadRequest request) {
        validarFaseSiAplica(request);

        Long idEmpleado = tokenManager.obtenerIdEmpleado(request.getUsername(), request.getPassword());
        String token    = tokenManager.obtenerToken(request.getUsername(), request.getPassword());

        List<Map<String, Object>> existentes = obtenerRegistrosExistentes(
                idEmpleado, request.getFechaRegistro().toString(), token, request);

        return registrarConSeparacion(request, idEmpleado, token, existentes);
    }

    private void validarFaseSiAplica(RegistrarActividadRequest request) {
        if (request.getIdTipoActividad() != null
                && request.getIdTipoActividad() == idTipoActividadServicio
                && (request.getFase() == null || request.getFase().isBlank())) {
            throw new NegocioException("El campo 'fase' es obligatorio cuando el tipo de actividad es Servicio");
        }
    }

    private boolean esTipoServicio(Object idTipoActividad) {
        if (idTipoActividad == null) return false;
        if (idTipoActividad instanceof Number n) return n.intValue() == idTipoActividadServicio;
        try {
            return Integer.parseInt(idTipoActividad.toString()) == idTipoActividadServicio;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // ─── Consulta de registros existentes ───────────────────────────────────

    @Override
    public List<Map<String, Object>> obtenerRegistrosPorEmpleadoYFecha(
            Long idEmpleado, String fecha, String username, String password) {
        String token = tokenManager.obtenerToken(username, password);
        try {
            Object raw = ejecutarGetConVariable(REGISTROS_POR_EMPLEADO_FECHA_URL, token, idEmpleado, fecha);
            return parsearListaDeRegistros(raw);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String nuevoToken = tokenManager.renovarToken(username, password);
                Object raw = ejecutarGetConVariable(REGISTROS_POR_EMPLEADO_FECHA_URL, nuevoToken, idEmpleado, fecha);
                return parsearListaDeRegistros(raw);
            }
            log.warn("No se pudieron obtener registros idEmpleado={} fecha={}: {}", idEmpleado, fecha, ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Error inesperado al obtener registros idEmpleado={} fecha={}: {}", idEmpleado, fecha, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> obtenerRegistrosExistentes(
            Long idEmpleado, String fecha, String token, RegistrarActividadRequest request) {
        try {
            Object raw = ejecutarGetConVariable(
                    REGISTROS_POR_EMPLEADO_FECHA_URL, token, idEmpleado, fecha);
            return parsearListaDeRegistros(raw);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String nuevoToken = tokenManager.renovarToken(request.getUsername(), request.getPassword());
                Object raw = ejecutarGetConVariable(
                        REGISTROS_POR_EMPLEADO_FECHA_URL, nuevoToken, idEmpleado, fecha);
                return parsearListaDeRegistros(raw);
            }
            log.warn("No se pudieron obtener registros existentes idEmpleado={} fecha={}: {}",
                    idEmpleado, fecha, ex.getMessage());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Error inesperado al obtener registros existentes: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsearListaDeRegistros(Object raw) {
        if (raw == null) return Collections.emptyList();
        List<Map<String, Object>> registros;
        if (raw instanceof List<?> list) {
            registros = (List<Map<String, Object>>) list;
        } else if (raw instanceof Map<?, ?> map && map.get("data") instanceof List<?> list) {
            registros = (List<Map<String, Object>>) list;
        } else {
            return Collections.emptyList();
        }
        registros.forEach(this::normalizarFase);
        return registros;
    }

    /**
     * Garantiza que cada registro expuesto al frontend incluya la clave "fase".
     * Si Scoca todavía no devuelve este campo, se publica como null para que el
     * contrato JSON sea estable independiente del backend remoto.
     */
    private void normalizarFase(Map<String, Object> registro) {
        if (registro == null) return;
        if (!registro.containsKey("fase")) registro.put("fase", null);
    }

    // ─── Mover registros superpuestos para dar espacio al nuevo ─────────────

    /**
     * Para cada registro de Scoca que se traslape con [newStart, newEnd]:
     *
     *   Hay antes (rS < nS) y después (rE > nE):
     *     → PUT existente a [nE, rE]  +  POST nuevo [rS, nS] (preserva las horas anteriores)
     *   Solo antes (rS < nS, rE <= nE):
     *     → PUT existente a [rS, nS]  (recorta su fin)
     *   Solo después (rS >= nS, rE > nE):
     *     → PUT existente a [nE, rE]  (mueve su inicio)
     *   Completamente dentro (rS >= nS, rE <= nE):
     *     → sin cambio (queda cubierto por el nuevo)
     *
     * Finalmente POST la nueva actividad en [newStart, newEnd].
     */
    private List<Object> registrarConSeparacion(RegistrarActividadRequest request,
                                                 Long idEmpleado, String token,
                                                 List<Map<String, Object>> existentes) {
        LocalTime newStart = LocalTime.parse(request.getHoraInicio(), TIME_FMT);
        LocalTime newEnd   = LocalTime.parse(request.getHoraFin(),    TIME_FMT);

        List<Map<String, Object>> superpuestos = existentes.stream()
                .filter(r -> r.get("horaInicio") != null && r.get("horaFin") != null && r.get("id") != null)
                .filter(r -> {
                    LocalTime rS = LocalTime.parse(r.get("horaInicio").toString(), TIME_PARSE);
                    LocalTime rE = LocalTime.parse(r.get("horaFin").toString(),    TIME_PARSE);
                    return rS.isBefore(newEnd) && rE.isAfter(newStart);
                })
                .collect(Collectors.toList());

        log.info("Registros superpuestos encontrados: {} para horario={}-{}",
                superpuestos.size(), request.getHoraInicio(), request.getHoraFin());

        List<Object> resultados = new ArrayList<>();

        for (Map<String, Object> reg : superpuestos) {
            Long      idReg = ((Number) reg.get("id")).longValue();
            LocalTime rS    = LocalTime.parse(reg.get("horaInicio").toString(), TIME_PARSE);
            LocalTime rE    = LocalTime.parse(reg.get("horaFin").toString(),    TIME_PARSE);

            boolean hayAntes   = rS.isBefore(newStart);
            boolean hayDespues = rE.isAfter(newEnd);

            if (hayAntes && hayDespues) {
                // El existente envuelve al nuevo: editar parte posterior, insertar parte anterior
                log.info("Registro id={} envuelve al nuevo [{}-{}], editando parte posterior y creando parte anterior",
                        idReg, rS.format(TIME_FMT), rE.format(TIME_FMT));
                resultados.add(actualizarRegistro(idReg,
                        buildUpdateBody(reg, idEmpleado, newEnd.format(TIME_FMT), rE.format(TIME_FMT)),
                        token, request));
                resultados.add(insertarRegistroExistente(reg, idEmpleado, token, request,
                        rS.format(TIME_FMT), newStart.format(TIME_FMT)));

            } else if (hayAntes) {
                // El existente empieza antes del nuevo y termina dentro: recortar su fin
                log.info("Registro id={} empieza antes del nuevo, recortando horaFin a {}", idReg, newStart.format(TIME_FMT));
                resultados.add(actualizarRegistro(idReg,
                        buildUpdateBody(reg, idEmpleado, rS.format(TIME_FMT), newStart.format(TIME_FMT)),
                        token, request));

            } else if (hayDespues) {
                // El existente empieza dentro del nuevo y termina después: mover inicio a newEnd
                log.info("Registro id={} termina después del nuevo, moviendo horaInicio a {}", idReg, newEnd.format(TIME_FMT));
                resultados.add(actualizarRegistro(idReg,
                        buildUpdateBody(reg, idEmpleado, newEnd.format(TIME_FMT), rE.format(TIME_FMT)),
                        token, request));

            } else {
                log.info("Registro id={} queda completamente dentro del nuevo intervalo, sin cambio", idReg);
            }
        }

        resultados.add(ejecutarRegistroConHorario(request, idEmpleado, token,
                request.getHoraInicio(), request.getHoraFin()));

        log.info("Separación completada: {} operación(es) idEmpleado={} fecha={} horario={}-{}",
                resultados.size(), idEmpleado, request.getFechaRegistro(),
                request.getHoraInicio(), request.getHoraFin());

        return resultados;
    }

    private Object insertarRegistroExistente(Map<String, Object> reg, Long idEmpleado,
                                              String token, RegistrarActividadRequest request,
                                              String horaInicio, String horaFin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idEmpleado",      idEmpleado);
        body.put("idActividad",     reg.get("idActividad"));
        body.put("idTipoActividad", reg.get("idTipoActividad"));
        body.put("idProyecto",      reg.get("idProyecto"));
        body.put("descripcion",     reg.get("descripcion"));
        body.put("fechaRegistro",   reg.get("fechaRegistro"));
        body.put("horaInicio",      horaInicio);
        body.put("horaFin",         horaFin);
        if (esTipoServicio(reg.get("idTipoActividad")) && reg.get("fase") != null) {
            body.put("fase", reg.get("fase"));
        }

        log.info("POST {} (parte anterior del existente) horaInicio={} horaFin={}",
                REGISTRAR_ACTIVIDAD_URL, horaInicio, horaFin);

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    REGISTRAR_ACTIVIDAD_URL, new HttpEntity<>(body, headers), Object.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                String nuevoToken = tokenManager.renovarToken(request.getUsername(), request.getPassword());
                headers.setBearerAuth(nuevoToken);
                ResponseEntity<Object> response = restTemplate.postForEntity(
                        REGISTRAR_ACTIVIDAD_URL, new HttpEntity<>(body, headers), Object.class);
                return response.getBody();
            }
            throw ex;
        }
    }

    private Object actualizarRegistro(Long idRegistro, Map<String, Object> body,
                                       String token, RegistrarActividadRequest request) {
        try {
            return doActualizarRegistro(idRegistro, body, token);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.warn("Token expirado al actualizar registro id={}, renovando...", idRegistro);
                return doActualizarRegistro(idRegistro, body,
                        tokenManager.renovarToken(request.getUsername(), request.getPassword()));
            }
            log.error("Error 4xx al actualizar registro id={} body={} response={}",
                    idRegistro, body, ex.getResponseBodyAsString());
            throw ex;
        } catch (HttpServerErrorException ex) {
            log.error("Error 5xx al actualizar registro id={} body={} response={}",
                    idRegistro, body, ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private Object doActualizarRegistro(Long idRegistro, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> bodyConId = new LinkedHashMap<>(body);
        bodyConId.put("id", idRegistro);

        log.info("PUT {} id={} horaInicio={} horaFin={}",
                REGISTRAR_ACTIVIDAD_URL, idRegistro, body.get("horaInicio"), body.get("horaFin"));

        ResponseEntity<Object> response = restTemplate.exchange(
                REGISTRAR_ACTIVIDAD_URL, HttpMethod.PUT,
                new HttpEntity<>(bodyConId, headers), Object.class);

        log.info("Registro actualizado id={}", idRegistro);
        return response.getBody();
    }

    private Map<String, Object> buildUpdateBody(Map<String, Object> reg, Long idEmpleado,
                                                 String horaInicio, String horaFin) {
        log.info("Campos del registro existente en Scoca: {}", reg.keySet());
        log.info("Valores del registro existente: {}", reg);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idEmpleado",      idEmpleado);
        body.put("idActividad",     reg.get("idActividad"));
        body.put("idTipoActividad", reg.get("idTipoActividad"));
        body.put("idProyecto",      reg.get("idProyecto"));
        body.put("descripcion",     reg.get("descripcion"));
        body.put("fechaRegistro",   reg.get("fechaRegistro"));
        body.put("horaInicio",      horaInicio);
        body.put("horaFin",         horaFin);
        if (esTipoServicio(reg.get("idTipoActividad")) && reg.get("fase") != null) {
            body.put("fase", reg.get("fase"));
        }
        return body;
    }

    private Object ejecutarRegistro(RegistrarActividadRequest request, Long idEmpleado, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idEmpleado",      idEmpleado);
        body.put("idActividad",     request.getIdActividad());
        body.put("idTipoActividad", request.getIdTipoActividad());
        body.put("idProyecto",      request.getIdProyecto());
        body.put("descripcion",     request.getDescripcion());
        body.put("fechaRegistro",   request.getFechaRegistro().toString());
        body.put("horaInicio",      request.getHoraInicio());
        body.put("horaFin",         request.getHoraFin());
        agregarFaseSiAplica(body, request);

        ResponseEntity<Object> response = restTemplate.postForEntity(
                REGISTRAR_ACTIVIDAD_URL, new HttpEntity<>(body, headers), Object.class);
        log.info("Actividad registrada en bitácora idEmpleado={} idProyecto={}", idEmpleado, request.getIdProyecto());
        return response.getBody();
    }

    private Object ejecutarRegistroConHorario(RegistrarActividadRequest request,
                                               Long idEmpleado, String token,
                                               String horaInicio, String horaFin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idEmpleado",      idEmpleado);
        body.put("idActividad",     request.getIdActividad());
        body.put("idTipoActividad", request.getIdTipoActividad());
        body.put("idProyecto",      request.getIdProyecto());
        body.put("descripcion",     request.getDescripcion());
        body.put("fechaRegistro",   request.getFechaRegistro().toString());
        body.put("horaInicio",      horaInicio);
        body.put("horaFin",         horaFin);
        body.put("fase", request.getFase());
        agregarFaseSiAplica(body, request);

        ResponseEntity<Object> response = restTemplate.postForEntity(
                REGISTRAR_ACTIVIDAD_URL, new HttpEntity<>(body, headers), Object.class);
        log.info("Franja registrada idEmpleado={} idProyecto={} horario={}-{}",
                idEmpleado, request.getIdProyecto(), horaInicio, horaFin);
        return response.getBody();
    }

    private void agregarFaseSiAplica(Map<String, Object> body, RegistrarActividadRequest request) {
        if (esTipoServicio(request.getIdTipoActividad())
                && request.getFase() != null
                && !request.getFase().isBlank()) {
            body.put("fase", request.getFase());
        }
    }

    private Object ejecutarConsulta(Long idEmpleado, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Object> response = restTemplate.exchange(
                BITACORA_URL, HttpMethod.GET, new HttpEntity<>(headers), Object.class, idEmpleado);
        log.info("Proyectos obtenidos idEmpleado={}", idEmpleado);
        return response.getBody();
    }

    private Object ejecutarGetSimple(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);
        log.info("GET {} completado", url);
        return response.getBody();
    }

    private Object ejecutarGetConVariable(String url, String token, Object... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Object.class, uriVars);
        log.info("GET {} vars={} completado", url, uriVars);
        return response.getBody();
    }

    private Object ejecutarGetPublico(String url) {
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, HttpEntity.EMPTY, Object.class);
        log.info("GET publico {} completado", url);
        return response.getBody();
    }

    private Object ejecutarGetPublicoConVariable(String url, Object... uriVars) {
        ResponseEntity<Object> response = restTemplate.exchange(
                url, HttpMethod.GET, HttpEntity.EMPTY, Object.class, uriVars);
        log.info("GET publico {} vars={} completado", url, uriVars);
        return response.getBody();
    }
}
