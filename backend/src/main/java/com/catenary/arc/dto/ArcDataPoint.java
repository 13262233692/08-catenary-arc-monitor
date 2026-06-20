package com.catenary.arc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArcDataPoint {

    private long timestamp;

    private String sectionId;

    private String sensorId;

    private double intensity;

    private double voltage;

    private double current;

    private double temperature;
}
