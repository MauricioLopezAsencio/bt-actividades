package com.spring.security.jwt.controller;

import com.spring.security.jwt.dto.ApiResponse;
import com.spring.security.jwt.dto.EstadisticasMesDto;
import com.spring.security.jwt.dto.EstadisticasRequest;
import com.spring.security.jwt.service.IEstadisticasService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/estadisticas")
@CrossOrigin("*")
@Slf4j
public class EstadisticasController {

    private final IEstadisticasService estadisticasService;

    public EstadisticasController(IEstadisticasService estadisticasService) {
        this.estadisticasService = estadisticasService;
    }

    @PostMapping("/mes")
    public ResponseEntity<ApiResponse<EstadisticasMesDto>> obtenerEstadisticasMes(
            @Valid @RequestBody EstadisticasRequest request,
            HttpServletRequest servletRequest) {

        EstadisticasMesDto data = estadisticasService.obtenerEstadisticasMes(request);
        return ResponseEntity.ok(ApiResponse.ok(data, "Estadísticas obtenidas exitosamente")
                .toBuilder().path(servletRequest.getRequestURI()).build());
    }
}
