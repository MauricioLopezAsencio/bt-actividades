package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.ActividadRequest;
import com.spring.security.jwt.dto.ActividadResultDto;

public interface IActividadService {

    ActividadResultDto obtenerActividades(ActividadRequest request);
}
