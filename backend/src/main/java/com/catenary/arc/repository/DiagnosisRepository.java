package com.catenary.arc.repository;

import com.catenary.arc.entity.ArcAlarmEvent;
import com.catenary.arc.entity.VideoRecord;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class DiagnosisRepository {

    private final ConcurrentHashMap<String, List<ArcAlarmEvent>> alarmEventsBySection = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ArcAlarmEvent> alarmEventsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VideoRecord> videoRecordsBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<VideoRecord>> videoRecordsBySection = new ConcurrentHashMap<>();

    private final Random random = new Random(42);

    @PostConstruct
    public void initDemoData() {
        long now = System.currentTimeMillis();
        long twentyFourHoursAgo = now - 24L * 60 * 60 * 1000;

        String[] sections = {"SEC-001", "SEC-002", "SEC-003", "SEC-004", "SEC-005", "SEC-006"};
        String[] trains = {"G1234", "G5678", "D2001", "D3045", "C1024", "G9081", "G7766", "D4433"};
        String[] cameras = {"ROOF-CAM-01", "ROOF-CAM-02", "POLE-CAM-A1", "POLE-CAM-B2", "AISLE-CAM-03"};
        String[] resolutions = {"1920x1080", "2560x1440", "1280x720"};

        for (String sectionId : sections) {
            int eventCount = 3 + random.nextInt(3);
            List<ArcAlarmEvent> sectionEvents = new ArrayList<>();
            List<VideoRecord> sectionVideos = new ArrayList<>();

            for (int i = 0; i < eventCount; i++) {
                String eventId = "EVT-" + sectionId + "-" + String.format("%04d", i + 1);
                String sensorId = "SNS-" + sectionId + "-" + String.format("%02d", 1 + random.nextInt(8));
                String videoSessionId = "VID-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();

                long eventStart = twentyFourHoursAgo + (long) (random.nextDouble() * (now - twentyFourHoursAgo));
                long duration = 100L + random.nextInt(7901);
                long eventEnd = eventStart + duration;

                long videoStart = eventStart - (5000L + random.nextInt(10000));
                long videoDuration = 30000L + random.nextInt(60000);
                long videoEnd = videoStart + videoDuration;

                double peakIntensity = 120.0 + random.nextDouble() * 330.0;
                double avgIntensity = peakIntensity * (0.4 + random.nextDouble() * 0.3);

                String level;
                if (peakIntensity >= 350.0) {
                    level = "CRITICAL";
                } else if (peakIntensity >= 220.0) {
                    level = "ALARM";
                } else {
                    level = "WARNING";
                }

                ArcAlarmEvent event = ArcAlarmEvent.builder()
                        .eventId(eventId)
                        .sectionId(sectionId)
                        .sensorId(sensorId)
                        .startTime(eventStart)
                        .endTime(eventEnd)
                        .peakIntensity(Math.round(peakIntensity * 100.0) / 100.0)
                        .avgIntensity(Math.round(avgIntensity * 100.0) / 100.0)
                        .durationMs(duration)
                        .level(level)
                        .videoSessionId(videoSessionId)
                        .createdTime(now)
                        .build();

                String trainId = trains[random.nextInt(trains.length)];
                String cameraId = cameras[random.nextInt(cameras.length)];
                String resolution = resolutions[random.nextInt(resolutions.length)];
                String hlsPlaylistUrl = "/hls/" + sectionId + "/" + videoSessionId + "/playlist.m3u8";
                String hlsBaseUrl = "/hls/" + sectionId + "/" + videoSessionId + "/";

                VideoRecord video = VideoRecord.builder()
                        .videoSessionId(videoSessionId)
                        .sectionId(sectionId)
                        .sensorId(sensorId)
                        .trainId(trainId)
                        .startTime(videoStart)
                        .endTime(videoEnd)
                        .durationMs(videoDuration)
                        .hlsPlaylistUrl(hlsPlaylistUrl)
                        .hlsBaseUrl(hlsBaseUrl)
                        .cameraId(cameraId)
                        .resolution(resolution)
                        .build();

                sectionEvents.add(event);
                sectionVideos.add(video);
                alarmEventsById.put(eventId, event);
                videoRecordsBySession.put(videoSessionId, video);
            }

            alarmEventsBySection.put(sectionId, sectionEvents);
            videoRecordsBySection.put(sectionId, sectionVideos);
        }

        int totalEvents = alarmEventsById.size();
        int totalVideos = videoRecordsBySession.size();
        log.info("Initialized demo diagnosis data: {} alarm events across {} sections, {} video records",
                totalEvents, sections.length, totalVideos);
    }

    public List<ArcAlarmEvent> findAlarmEvents(String sectionId, long startMs, long endMs, double minIntensity) {
        List<ArcAlarmEvent> events = alarmEventsBySection.getOrDefault(sectionId, new ArrayList<>());
        return events.stream()
                .filter(e -> e.getStartTime() >= startMs)
                .filter(e -> e.getEndTime() <= endMs)
                .filter(e -> e.getPeakIntensity() >= minIntensity)
                .collect(Collectors.toList());
    }

    public ArcAlarmEvent findAlarmEventById(String eventId) {
        return alarmEventsById.get(eventId);
    }

    public VideoRecord findVideoBySession(String videoSessionId) {
        return videoRecordsBySession.get(videoSessionId);
    }

    public VideoRecord findVideoByTimestamp(String sectionId, long timestamp) {
        List<VideoRecord> videos = videoRecordsBySection.getOrDefault(sectionId, new ArrayList<>());
        return videos.stream()
                .filter(v -> timestamp >= v.getStartTime() && timestamp <= v.getEndTime())
                .findFirst()
                .orElse(null);
    }

    public List<VideoRecord> findVideosBySectionAndRange(String sectionId, long startMs, long endMs) {
        List<VideoRecord> videos = videoRecordsBySection.getOrDefault(sectionId, new ArrayList<>());
        return videos.stream()
                .filter(v -> v.getEndTime() >= startMs && v.getStartTime() <= endMs)
                .collect(Collectors.toList());
    }

    public List<ArcAlarmEvent> findAllAlarmEvents() {
        return new ArrayList<>(alarmEventsById.values());
    }
}
