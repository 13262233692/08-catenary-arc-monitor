package com.catenary.arc.dto;

import lombok.Data;

@Data
public class DiagnosisRequest {

    private String sectionId;

    private long startMs;

    private long endMs;

    private double minIntensity = 120.0;
}
