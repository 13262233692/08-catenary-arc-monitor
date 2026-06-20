package com.catenary.arc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArcAlarmEvent {

    private String eventId;

    private String sectionId;

    private String sensorId;

    private long startTime;

    private long endTime;

    private double peakIntensity;

    private double avgIntensity;

    private long durationMs;

    private String level;

    private String videoSessionId;

    private long createdTime;
}
