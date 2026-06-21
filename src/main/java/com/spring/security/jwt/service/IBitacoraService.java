package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.RegistrarActividadRequest;

import java.util.List;
import java.util.Map;

public interface IBitacoraService {

    Object obtenerProyectosPorEmpleado(Long idEmpleado, String username, String password);

    Long obtenerIdEmpleado(String username, String password);

    Object registrarActividad(RegistrarActividadRequest request);

    List<Object> registrarActividadConParticion(RegistrarActividadRequest request);

    Object obtenerTiposActividad(String username, String password);

    Object obtenerActividadesPorTipo(Integer idTipoActividad, String username, String password);

    List<Map<String, Object>> obtenerRegistrosPorEmpleadoYFecha(Long idEmpleado, String fecha, String username, String password);

}
