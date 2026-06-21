package com.spring.security.jwt.controller;

import com.spring.security.jwt.dto.ApiResponse;
import com.spring.security.jwt.dto.BitacoraProyectosRequest;
import com.spring.security.jwt.dto.RegistrarActividadRequest;
import com.spring.security.jwt.dto.RegistrosPorFechaRequest;
import com.spring.security.jwt.service.IBitacoraService;
import com.spring.security.jwt.util.LogBanner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/bitacora")
@Slf4j
public class BitacoraController {

    private final IBitacoraService bitacoraService;

    public BitacoraController(IBitacoraService bitacoraService) {
        this.bitacoraService = bitacoraService;
    }

    @PostMapping("/actividades")
    public ResponseEntity<ApiResponse<List<Object>>> registrarActividad(
            @Valid @RequestBody RegistrarActividadRequest request,
            HttpServletRequest servletRequest) {

        long t0 = LogBanner.inicio(log, "Registrar actividad en bitácora");
        try {
            List<Object> data = bitacoraService.registrarActividadConParticion(request);

            if (data.isEmpty()) {
                log.warn("Horario ya cubierto por registros existentes, nada que registrar");
                LogBanner.fin(log, "Registrar actividad en bitácora (sin cambios)", t0);
                return ResponseEntity.unprocessableEntity()
                        .body(ApiResponse.<List<Object>>builder()
                                .status(422)
                                .message("El horario solicitado ya está completamente cubierto por registros existentes")
                                .errorCode("HORARIO_CUBIERTO")
                                .path(servletRequest.getRequestURI())
                                .build());
            }

            String mensaje = data.size() == 1
                    ? "Actividad registrada exitosamente"
                    : data.size() + " franjas registradas por traslape con actividades existentes";

            log.info("Actividad registrada: {} franja(s)", data.size());
            LogBanner.fin(log, "Registrar actividad en bitácora", t0);
            return ResponseEntity.ok(ApiResponse.ok(data, mensaje)
                    .toBuilder().path(servletRequest.getRequestURI()).build());

        } catch (HttpClientErrorException ex) {
            HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
            String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error desconocido";
            log.error("Error al registrar actividad en bitácora status={}", ex.getStatusCode());
            LogBanner.fin(log, "Registrar actividad en bitácora CON ERROR", t0);
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.<List<Object>>builder()
                            .status(ex.getStatusCode().value())
                            .message("Error al registrar actividad en bitácora: " + reason)
                            .errorCode("BITACORA_API_ERROR")
                            .path(servletRequest.getRequestURI())
                            .build());
        }
    }

    @PostMapping("/proyectos/byEmpleado")
    public ResponseEntity<ApiResponse<Object>> obtenerProyectosPorEmpleado(
            @Valid @RequestBody BitacoraProyectosRequest request,
            HttpServletRequest servletRequest) {

        long t0 = LogBanner.inicio(log, "Obtener proyectos por empleado username=" + request.getUsername());
        try {
            Long idEmpleado = bitacoraService.obtenerIdEmpleado(request.getUsername(), request.getPassword());
            Object data = bitacoraService.obtenerProyectosPorEmpleado(
                    idEmpleado,
                    request.getUsername(),
                    request.getPassword()
            );

            LogBanner.fin(log, "Obtener proyectos por empleado idEmpleado=" + idEmpleado, t0);
            return ResponseEntity.ok(ApiResponse.ok(data, "Proyectos obtenidos exitosamente")
                    .toBuilder().path(servletRequest.getRequestURI()).build());

        } catch (HttpClientErrorException ex) {
            HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
            String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error desconocido";
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.<Object>builder()
                            .status(ex.getStatusCode().value())
                            .message("Error al consultar bitácora: " + reason)
                            .errorCode("BITACORA_API_ERROR")
                            .path(servletRequest.getRequestURI())
                            .build());
        }
    }

    @PostMapping("/tipoActividad")
    public ResponseEntity<ApiResponse<Object>> obtenerTiposActividad(
            @Valid @RequestBody BitacoraProyectosRequest request,
            HttpServletRequest servletRequest) {

        long t0 = LogBanner.inicio(log, "Obtener tipos de actividad username=" + request.getUsername());
        try {
            Object data = bitacoraService.obtenerTiposActividad(request.getUsername(), request.getPassword());
            LogBanner.fin(log, "Obtener tipos de actividad", t0);
            return ResponseEntity.ok(ApiResponse.ok(data, "Tipos de actividad obtenidos exitosamente")
                    .toBuilder().path(servletRequest.getRequestURI()).build());

        } catch (HttpClientErrorException ex) {
            HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
            String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error desconocido";
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.<Object>builder()
                            .status(ex.getStatusCode().value())
                            .message("Error al consultar tipos de actividad: " + reason)
                            .errorCode("BITACORA_API_ERROR")
                            .path(servletRequest.getRequestURI())
                            .build());
        }
    }

    @GetMapping("/actividades/{idTipoActividad}")
    public ResponseEntity<ApiResponse<Object>> obtenerActividadesPorTipo(
            @PathVariable @Positive Integer idTipoActividad,
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest servletRequest) {

        long t0 = LogBanner.inicio(log, "Obtener actividades por tipo idTipoActividad=" + idTipoActividad);
        try {
            Object data = bitacoraService.obtenerActividadesPorTipo(idTipoActividad, username, password);
            LogBanner.fin(log, "Obtener actividades por tipo idTipoActividad=" + idTipoActividad, t0);
            return ResponseEntity.ok(ApiResponse.ok(data, "Actividades obtenidas exitosamente")
                    .toBuilder().path(servletRequest.getRequestURI()).build());

        } catch (HttpClientErrorException ex) {
            HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
            String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error desconocido";
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.<Object>builder()
                            .status(ex.getStatusCode().value())
                            .message("Error al consultar actividades: " + reason)
                            .errorCode("BITACORA_API_ERROR")
                            .path(servletRequest.getRequestURI())
                            .build());
        }
    }

    @PostMapping("/registros/byFecha")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> obtenerRegistrosPorFecha(
            @Valid @RequestBody RegistrosPorFechaRequest request,
            HttpServletRequest servletRequest) {

        String proceso = "Obtener registros por fecha=" + request.getFecha() + " username=" + request.getUsername();
        long t0 = LogBanner.inicio(log, proceso);

        Long idEmpleado = bitacoraService.obtenerIdEmpleado(request.getUsername(), request.getPassword());
        List<Map<String, Object>> data = bitacoraService.obtenerRegistrosPorEmpleadoYFecha(
                idEmpleado, request.getFecha().toString(),
                request.getUsername(), request.getPassword());

        log.info("Registros obtenidos: {}", data.size());
        LogBanner.fin(log, proceso, t0);
        return ResponseEntity.ok(ApiResponse.ok(data, "Registros obtenidos exitosamente")
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }
}
