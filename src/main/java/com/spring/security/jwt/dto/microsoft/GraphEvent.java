package com.spring.security.jwt.dto.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphEvent {

    private String subject;
    private String type;           // singleInstance, occurrence, exception, seriesMaster
    private GraphEventDateTime start;
    private GraphEventDateTime end;
    private GraphRecurrence recurrence;
    private List<GraphAttendee> attendees;
}
