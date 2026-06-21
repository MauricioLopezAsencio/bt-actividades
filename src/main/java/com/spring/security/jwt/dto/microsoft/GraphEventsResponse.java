package com.spring.security.jwt.dto.microsoft;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GraphEventsResponse {

    private List<GraphEvent> value;

    @JsonProperty("@odata.nextLink")
    private String nextLink;
}
