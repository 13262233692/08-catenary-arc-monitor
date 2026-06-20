package com.catenary.arc.controller;

import com.catenary.arc.dto.ApiResponse;
import com.catenary.arc.dto.DiagnosisEventResponse;
import com.catenary.arc.dto.DiagnosisRequest;
import com.catenary.arc.entity.ArcAlarmEvent;
import com.catenary.arc.entity.VideoRecord;
import com.catenary.arc.service.DiagnosisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/diagnosis")
@RequiredArgsConstructor
public class DiagnosisController {

    private final DiagnosisService diagnosisService;

    @PostMapping("/events")
    public ApiResponse<List<ArcAlarmEvent>> getAlarmEvents(@RequestBody DiagnosisRequest request) {
        List<ArcAlarmEvent> events = diagnosisService.getAlarmEvents(request);
        return ApiResponse.success(events);
    }

    @GetMapping("/event/{eventId}")
    public ApiResponse<DiagnosisEventResponse> getEventDetail(@PathVariable String eventId) {
        DiagnosisEventResponse response = diagnosisService.getEventDetail(eventId);
        if (response == null) {
            return ApiResponse.error(404, "Event not found");
        }
        return ApiResponse.success(response);
    }

    @GetMapping("/video/by-timestamp")
    public ApiResponse<VideoRecord> getVideoByTimestamp(
            @RequestParam String sectionId,
            @RequestParam long timestamp) {
        VideoRecord video = diagnosisService.getVideoForTimestamp(sectionId, timestamp);
        if (video == null) {
            return ApiResponse.error(404, "No video found for the given timestamp");
        }
        return ApiResponse.success(video);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getDiagnosisStats(
            @RequestParam String sectionId,
            @RequestParam long startMs,
            @RequestParam long endMs) {
        Map<String, Object> stats = diagnosisService.getDiagnosisStats(sectionId, startMs, endMs);
        return ApiResponse.success(stats);
    }
}
