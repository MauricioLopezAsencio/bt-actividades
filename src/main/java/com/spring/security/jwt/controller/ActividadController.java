package com.spring.security.jwt.controller;

import com.spring.security.jwt.dto.ActividadDto;
import com.spring.security.jwt.dto.ActividadRequest;
import com.spring.security.jwt.dto.ActividadResultDto;
import com.spring.security.jwt.dto.ApiResponse;
import com.spring.security.jwt.dto.Fase;
import com.spring.security.jwt.dto.FaseDto;
import com.spring.security.jwt.dto.WorkItemDto;
import com.spring.security.jwt.service.IActividadService;
import com.spring.security.jwt.service.MapeoTipoActividadFase;
import com.spring.security.jwt.service.WorkItemCsvService;
import com.spring.security.jwt.util.LogBanner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/actividades")
@Slf4j
public class ActividadController {

    private final IActividadService        actividadService;
    private final WorkItemCsvService       workItemCsvService;
    private final MapeoTipoActividadFase   mapeoFase;

    public ActividadController(IActividadService actividadService,
                               WorkItemCsvService workItemCsvService,
                               MapeoTipoActividadFase mapeoFase) {
        this.actividadService   = actividadService;
        this.workItemCsvService = workItemCsvService;
        this.mapeoFase          = mapeoFase;
    }

    @GetMapping("/fases")
    public ResponseEntity<ApiResponse<List<FaseDto>>> obtenerFases(HttpServletRequest servletRequest) {
        List<FaseDto> data = Fase.ordenadas().stream()
                .map(FaseDto::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data, "Fases obtenidas exitosamente")
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }

    @GetMapping("/fases/mapeo")
    public ResponseEntity<ApiResponse<Map<String, String>>> obtenerMapeoFase(HttpServletRequest servletRequest) {
        Map<String, String> data = mapeoFase.obtenerMapeo().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getCodigo()));
        return ResponseEntity.ok(ApiResponse.ok(data, "Mapeo actividad → fase obtenido exitosamente")
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActividadResultDto>> obtenerActividades(
            @Valid @RequestBody ActividadRequest request,
            HttpServletRequest servletRequest) {

        String proceso = "Obtener actividades " + request.getFechaInicio() + " a " + request.getFechaFin()
                + " username=" + request.getUsername();
        long t0 = LogBanner.inicio(log, proceso);

        ActividadResultDto data = actividadService.obtenerActividades(request);

        LogBanner.fin(log, proceso, t0);
        return ResponseEntity.ok(ApiResponse.ok(data, "Actividades obtenidas exitosamente")
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }

    @PostMapping(value = "/work-items/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<WorkItemDto>>> importarWorkItems(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest servletRequest) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<WorkItemDto>>builder()
                            .status(400)
                            .message("El archivo CSV está vacío")
                            .errorCode("ARCHIVO_VACIO")
                            .path(servletRequest.getRequestURI())
                            .build());
        }

        long t0 = LogBanner.inicio(log, "Importar work items CSV archivo=" + file.getOriginalFilename());

        List<WorkItemDto> items = workItemCsvService.parsear(file);
        String mensaje = items.size() + " work item(s) importados exitosamente";

        log.info("Importación CSV completada: {}", mensaje);
        LogBanner.fin(log, "Importar work items CSV", t0);
        return ResponseEntity.ok(ApiResponse.ok(items, mensaje)
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }

    @PostMapping(value = "/work-items/preparar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<ActividadDto>>> prepararWorkItems(
            @RequestParam("file")     MultipartFile file,
            @RequestParam(value = "fecha", required = false) String fecha,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest servletRequest) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ActividadDto>>builder()
                            .status(400)
                            .message("El archivo CSV está vacío")
                            .errorCode("ARCHIVO_VACIO")
                            .path(servletRequest.getRequestURI())
                            .build());
        }

        long t0 = LogBanner.inicio(log, "Preparar work items fecha=" + fecha + " username=" + username);

        List<ActividadDto> actividades = workItemCsvService.preparar(file, fecha, username, password);
        String mensaje = actividades.size() + " actividad(es) preparadas desde work items";

        log.info("Work items preparados: {} fecha={}", actividades.size(), fecha);
        LogBanner.fin(log, "Preparar work items fecha=" + fecha, t0);
        return ResponseEntity.ok(ApiResponse.ok(actividades, mensaje)
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }
}
