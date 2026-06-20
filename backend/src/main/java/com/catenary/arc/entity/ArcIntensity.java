package com.catenary.arc.entity;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArcIntensity {

    private Instant time;

    private String sectionId;

    private String sensorId;

    private double intensity;

    private double voltage;

    private double current;

    private double temperature;
}
