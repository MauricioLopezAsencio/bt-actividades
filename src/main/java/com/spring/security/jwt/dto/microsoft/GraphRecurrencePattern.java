package com.spring.security.jwt.dto.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphRecurrencePattern {

    private String type;       // daily, weekly, absoluteMonthly, relativeMonthly, absoluteYearly, relativeYearly
    private int interval;
    private List<String> daysOfWeek;
    private int dayOfMonth;
    private int month;
    private String index;          // first, second, third, fourth, last
    private String firstDayOfWeek;
}
