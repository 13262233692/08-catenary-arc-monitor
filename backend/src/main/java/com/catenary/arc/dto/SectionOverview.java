package com.catenary.arc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionOverview {

    private String sectionId;

    private String sectionName;

    private String lineName;

    private double startKm;

    private double endKm;

    private String status;

    private double latestIntensity;

    private double avgIntensity;

    private double maxIntensity;

    private int alarmCount;

    private int sensorCount;
}
