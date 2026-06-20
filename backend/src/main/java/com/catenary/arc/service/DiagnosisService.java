package com.catenary.arc.service;

import com.catenary.arc.dto.DiagnosisEventResponse;
import com.catenary.arc.dto.DiagnosisRequest;
import com.catenary.arc.entity.ArcAlarmEvent;
import com.catenary.arc.entity.VideoRecord;
import com.catenary.arc.repository.DiagnosisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;

    public List<ArcAlarmEvent> getAlarmEvents(DiagnosisRequest request) {
        log.debug("Querying alarm events for sectionId={}, range={}-{}, minIntensity={}",
                request.getSectionId(), request.getStartMs(), request.getEndMs(), request.getMinIntensity());
        return diagnosisRepository.findAlarmEvents(
                request.getSectionId(),
                request.getStartMs(),
                request.getEndMs(),
                request.getMinIntensity()
        );
    }

    public DiagnosisEventResponse getEventDetail(String eventId) {
        log.debug("Getting event detail for eventId={}", eventId);
        ArcAlarmEvent event = diagnosisRepository.findAlarmEventById(eventId);
        if (event == null) {
            log.warn("Event not found for eventId={}", eventId);
            return null;
        }

        VideoRecord video = diagnosisRepository.findVideoBySession(event.getVideoSessionId());
        long offsetMs = 0;
        if (video != null) {
            offsetMs = event.getStartTime() - video.getStartTime();
            if (offsetMs < 0) {
                offsetMs = 0;
            }
        }

        return DiagnosisEventResponse.builder()
                .event(event)
                .video(video)
                .eventVideoTimeOffsetMs(offsetMs)
                .build();
    }

    public VideoRecord getVideoForTimestamp(String sectionId, long timestamp) {
        log.debug("Getting video for sectionId={}, timestamp={}", sectionId, timestamp);
        return diagnosisRepository.findVideoByTimestamp(sectionId, timestamp);
    }

    public Map<String, Object> getDiagnosisStats(String sectionId, long startMs, long endMs) {
        log.debug("Getting diagnosis stats for sectionId={}, range={}-{}", sectionId, startMs, endMs);
        List<ArcAlarmEvent> events = diagnosisRepository.findAlarmEvents(sectionId, startMs, endMs, 0.0);

        int totalEvents = events.size();
        int criticalCount = 0;
        int warningCount = 0;
        int alarmCount = 0;
        long totalDuration = 0;
        double maxPeak = 0.0;

        for (ArcAlarmEvent event : events) {
            String level = event.getLevel();
            if ("CRITICAL".equals(level)) {
                criticalCount++;
            } else if ("WARNING".equals(level)) {
                warningCount++;
            } else if ("ALARM".equals(level)) {
                alarmCount++;
            }
            totalDuration += event.getDurationMs();
            if (event.getPeakIntensity() > maxPeak) {
                maxPeak = event.getPeakIntensity();
            }
        }

        double avgDuration = totalEvents > 0 ? (double) totalDuration / totalEvents : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", totalEvents);
        stats.put("criticalCount", criticalCount);
        stats.put("alarmCount", alarmCount);
        stats.put("warningCount", warningCount);
        stats.put("avgDuration", Math.round(avgDuration * 100.0) / 100.0);
        stats.put("maxPeak", Math.round(maxPeak * 100.0) / 100.0);
        stats.put("sectionId", sectionId);
        stats.put("startMs", startMs);
        stats.put("endMs", endMs);

        return stats;
    }
}
