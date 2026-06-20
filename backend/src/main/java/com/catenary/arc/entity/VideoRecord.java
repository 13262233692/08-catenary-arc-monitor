package com.catenary.arc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoRecord {

    private String videoSessionId;

    private String sectionId;

    private String sensorId;

    private String trainId;

    private long startTime;

    private long endTime;

    private long durationMs;

    private String hlsPlaylistUrl;

    private String hlsBaseUrl;

    private String cameraId;

    private String resolution;
}
