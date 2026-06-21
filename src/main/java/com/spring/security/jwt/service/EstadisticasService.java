package com.spring.security.jwt.service;

import com.spring.security.jwt.dto.EstadisticasMesDto;
import com.spring.security.jwt.dto.EstadisticasRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EstadisticasService implements IEstadisticasService {

    private static final String[] NOMBRES_MESES = {
        "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private static final DateTimeFormatter TIME_PARSE = DateTimeFormatter.ofPattern("HH:mm[:ss]");

    private final IBitacoraService       bitacoraService;
    private final FeriadosMexicoService  feriadosService;

    public EstadisticasService(IBitacoraService bitacoraService,
                                FeriadosMexicoService feriadosService) {
        this.bitacoraService  = bitacoraService;
        this.feriadosService  = feriadosService;
    }

    @Override
    public EstadisticasMesDto obtenerEstadisticasMes(EstadisticasRequest request) {
        Long idEmpleado = bitacoraService.obtenerIdEmpleado(request.getUsername(), request.getPassword());

        List<LocalDate> diasHabiles = calcularDiasHabiles(request.getMes(), request.getAnio());

        int    diasConRegistro   = 0;
        double minutosRegistrados = 0;

        for (LocalDate dia : diasHabiles) {
            List<Map<String, Object>> registros = bitacoraService.obtenerRegistrosPorEmpleadoYFecha(
                    idEmpleado, dia.toString(), request.getUsername(), request.getPassword());

            if (!registros.isEmpty()) {
                diasConRegistro++;
                for (Map<String, Object> reg : registros) {
                    minutosRegistrados += calcularMinutos(reg);
                }
            }
        }

        double horasEsperadas   = diasHabiles.size() * 8.0;
        double horasRegistradas = Math.round((minutosRegistrados / 60.0) * 100.0) / 100.0;
        double porcentaje       = horasEsperadas > 0
                ? Math.min(100.0, Math.round((horasRegistradas / horasEsperadas * 100.0) * 100.0) / 100.0)
                : 0.0;

        log.info("Estadísticas mes={}/{} idEmpleado={} diasHabiles={} diasConRegistro={} horas={}/{}  pct={}%",
                request.getMes(), request.getAnio(), idEmpleado,
                diasHabiles.size(), diasConRegistro, horasRegistradas, horasEsperadas, porcentaje);

        return EstadisticasMesDto.builder()
                .nombreMes(NOMBRES_MESES[request.getMes()] + " " + request.getAnio())
                .mes(request.getMes())
                .anio(request.getAnio())
                .diasHabiles(diasHabiles.size())
                .diasConRegistro(diasConRegistro)
                .horasEsperadas(horasEsperadas)
                .horasRegistradas(horasRegistradas)
                .porcentaje(porcentaje)
                .build();
    }

    private List<LocalDate> calcularDiasHabiles(int mes, int anio) {
        LocalDate inicio  = LocalDate.of(anio, mes, 1);
        LocalDate fin     = inicio.withDayOfMonth(inicio.lengthOfMonth());
        List<LocalDate> dias = new ArrayList<>();

        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY
                    && !feriadosService.esFeriado(d)) {
                dias.add(d);
            }
        }
        return dias;
    }

    private double calcularMinutos(Map<String, Object> registro) {
        Object inicio = registro.get("horaInicio");
        Object fin    = registro.get("horaFin");
        if (inicio == null || fin == null) return 0;
        try {
            var t1      = java.time.LocalTime.parse(inicio.toString(), TIME_PARSE);
            var t2      = java.time.LocalTime.parse(fin.toString(),    TIME_PARSE);
            long minutos = java.time.Duration.between(t1, t2).toMinutes();
            return Math.max(0, minutos);
        } catch (DateTimeParseException ex) {
            log.warn("No se pudo calcular duración horaInicio={} horaFin={}", inicio, fin);
            return 0;
        }
    }
}
