package com.catenary.arc.dto;

import com.catenary.arc.entity.ArcAlarmEvent;
import com.catenary.arc.entity.VideoRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisEventResponse {

    private ArcAlarmEvent event;

    private VideoRecord video;

    private long eventVideoTimeOffsetMs;
}
