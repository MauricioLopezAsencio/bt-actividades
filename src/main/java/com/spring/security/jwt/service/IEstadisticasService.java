package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.EstadisticasMesDto;
import com.spring.security.jwt.dto.EstadisticasRequest;

public interface IEstadisticasService {

    EstadisticasMesDto obtenerEstadisticasMes(EstadisticasRequest request);
}
