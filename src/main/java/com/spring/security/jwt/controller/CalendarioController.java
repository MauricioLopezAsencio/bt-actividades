package com.spring.security.jwt.controller;

import com.spring.security.jwt.dto.ApiResponse;
import com.spring.security.jwt.dto.CalendarioEventoDto;
import com.spring.security.jwt.service.ICalendarioService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/calendario")
@CrossOrigin("*")
@Slf4j
public class CalendarioController {

    private final ICalendarioService calendarioService;

    public CalendarioController(ICalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping("/eventos")
    public ResponseEntity<ApiResponse<List<CalendarioEventoDto>>> obtenerEventos(
            @RequestHeader("Authorization") String authorizationHeader,
            HttpServletRequest request) {

        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7).trim()
                : authorizationHeader.trim();

        try {
            List<CalendarioEventoDto> data = calendarioService.obtenerEventos(token);
            return ResponseEntity.ok(ApiResponse.ok(data, "Eventos obtenidos exitosamente")
                    .toBuilder().path(request.getRequestURI()).build());
        } catch (HttpClientErrorException ex) {
            log.error("Error al obtener eventos de Microsoft Graph path={} status={}",
                    request.getRequestURI(), ex.getStatusCode());
            HttpStatus httpStatus = HttpStatus.resolve(ex.getStatusCode().value());
            String reason = httpStatus != null ? httpStatus.getReasonPhrase() : "Error desconocido";
            return ResponseEntity.status(ex.getStatusCode())
                    .body(ApiResponse.<List<CalendarioEventoDto>>builder()
                            .status(ex.getStatusCode().value())
                            .message("Error al consultar Microsoft Graph: " + reason)
                            .errorCode("GRAPH_API_ERROR")
                            .path(request.getRequestURI())
                            .build());
        }
    }
}
