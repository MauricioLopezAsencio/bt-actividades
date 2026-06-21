package com.spring.security.jwt.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarioEventoDto {

    private String subject;
    private String start;
    private String end;
    private String type;
    private String startDate;
    private String endDate;
    private String modalidad;   // interna | externa
}
