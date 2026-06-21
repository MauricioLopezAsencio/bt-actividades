package com.spring.security.jwt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkItemDto {

    private Integer id;
    private String  workItemType;
    private String  state;
    private String  title;
    private String  iterationPath;
    private Integer minutosWorked;
}
