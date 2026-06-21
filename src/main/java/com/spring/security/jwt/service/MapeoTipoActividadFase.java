package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.Fase;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapeo del nombre de actividad (idActividad) a la fase por defecto.
 * Solo aplica cuando el tipo de actividad seleccionado en Scoca es "Servicio".
 *
 * La búsqueda es tolerante a mayúsculas/minúsculas y a acentos. Si el nombre
 * no está en el catálogo, la fase por defecto es {@link Fase#NO_APLICA}.
 */
@Service
public class MapeoTipoActividadFase {

    private final Map<String, Fase> mapeo;

    public MapeoTipoActividadFase() {
        Map<String, Fase> base = new LinkedHashMap<>();
        base.put("Análisis",                       Fase.ANALISIS_DISENO);
        base.put("Arquitectura",                   Fase.DESARROLLO_CONSTRUCCION);
        base.put("Atención de defecto",            Fase.PRUEBAS);
        base.put("Bases de datos",                 Fase.DESARROLLO_CONSTRUCCION);
        base.put("Capacitación",                   Fase.DESPLIEGUE);
        base.put("Capacitación al usuario",        Fase.DESPLIEGUE);
        base.put("Codificación",                   Fase.DESARROLLO_CONSTRUCCION);
        base.put("Desarrollo",                     Fase.DESARROLLO_CONSTRUCCION);
        base.put("Despliegue",                     Fase.DESPLIEGUE);
        base.put("Diseño",                         Fase.ANALISIS_DISENO);
        base.put("Diversos",                       Fase.GARANTIA);
        base.put("Elaboración de documentos",      Fase.ANALISIS_DISENO);
        base.put("Entregables",                    Fase.DESPLIEGUE);
        base.put("Implementación",                 Fase.DESPLIEGUE);
        base.put("Investigación",                  Fase.ANALISIS_DISENO);
        base.put("Legales y trámites",             Fase.GARANTIA);
        base.put("Plan de trabajo",                Fase.ANALISIS_DISENO);
        base.put("Pruebas",                        Fase.PRUEBAS);
        base.put("Reportes",                       Fase.GARANTIA);
        base.put("Seguimiento a cumplimiento",     Fase.GARANTIA);
        base.put("Seguridad de la información",    Fase.DESARROLLO_CONSTRUCCION);
        base.put("Sesión externa",                 Fase.GARANTIA);
        base.put("Sesión interna",                 Fase.GARANTIA);
        base.put("Soporte",                        Fase.GARANTIA);
        base.put("Tableros",                       Fase.DESARROLLO_CONSTRUCCION);
        base.put("Ventas/comercial",               Fase.GARANTIA);

        Map<String, Fase> normalizado = new LinkedHashMap<>();
        base.forEach((k, v) -> normalizado.put(normalizar(k), v));
        this.mapeo = Collections.unmodifiableMap(normalizado);
    }

    public Fase resolver(String nombreActividad) {
        if (nombreActividad == null || nombreActividad.isBlank()) {
            return Fase.NO_APLICA;
        }
        return mapeo.getOrDefault(normalizar(nombreActividad), Fase.NO_APLICA);
    }

    public Map<String, Fase> obtenerMapeo() {
        return mapeo;
    }

    private static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinAcentos.trim().toLowerCase();
    }
}
