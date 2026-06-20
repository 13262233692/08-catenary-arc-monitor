package com.catenary.arc.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;

@Data
public class ArcDataQueryRequest {

    private String sectionId;

    private long startMs;

    private long endMs;

    private String interval = "1s";

    private int maxPoints = 2000;

    @AssertTrue(message = "Time range too large")
    public boolean isTimeRangeValid() {
        return (endMs - startMs) <= 86400000;
    }
}
