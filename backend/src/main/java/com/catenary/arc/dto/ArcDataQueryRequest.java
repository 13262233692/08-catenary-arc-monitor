package com.catenary.arc.dto;

import lombok.Data;

@Data
public class ArcDataQueryRequest {

    private String sectionId;

    private long startMs;

    private long endMs;

    private String interval = "1s";

    private int maxPoints = 2000;
}
