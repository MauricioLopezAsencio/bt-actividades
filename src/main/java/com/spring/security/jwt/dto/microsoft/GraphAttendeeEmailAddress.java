package com.spring.security.jwt.dto.microsoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphAttendeeEmailAddress {

    private String address;
    private String name;
}
