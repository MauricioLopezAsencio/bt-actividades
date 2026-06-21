package com.spring.security.jwt;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class MsSpringSecurityJwtApplication {

    @Value("${app.timezone:America/Mexico_City}")
    private String appTimezone;

    public static void main(String[] args) {
        SpringApplication.run(MsSpringSecurityJwtApplication.class, args);
    }

    /**
     * Fuerza el timezone del JVM al arrancar la aplicación.
     * Garantiza que LocalDate.now(), LocalTime.now(), LocalDateTime.now()
     * y ZoneId.systemDefault() devuelvan la hora de México,
     * independientemente del timezone del servidor (Virginia = US/Eastern).
     */
    @PostConstruct
    public void configurarTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(appTimezone));
    }

}
