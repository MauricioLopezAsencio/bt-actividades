package com.spring.security.jwt.dto.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphRecurrenceRange {

    private String type;               // endDate, noEnd, numbered
    private String startDate;          // yyyy-MM-dd
    private String endDate;            // yyyy-MM-dd (null when type=noEnd)
    private int numberOfOccurrences;   // usado cuando type=numbered
    private String recurrenceTimeZone;
}
