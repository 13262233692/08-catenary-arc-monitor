package com.catenary.arc.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.catenary.arc.dto.SectionOverview;
import com.catenary.arc.entity.CatenarySection;
import com.catenary.arc.repository.ArcIntensityRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectionMonitorService {

    private final ArcIntensityRepository arcIntensityRepository;

    private final List<CatenarySection> sections = new ArrayList<>();

    @PostConstruct
    public void init() {
        sections.add(new CatenarySection("SEC001", "京沪高铁-K100+200至K105+500", "京沪高铁", 100.2, 105.5, "B001", "S001", "W001", "NORMAL"));
        sections.add(new CatenarySection("SEC002", "京沪高铁-K120+100至K125+800", "京沪高铁", 120.1, 125.8, "B001", "S002", "W002", "WARNING"));
        sections.add(new CatenarySection("SEC003", "京沪高铁-K150+300至K155+600", "京沪高铁", 150.3, 155.6, "B001", "S003", "W003", "ALARM"));
        sections.add(new CatenarySection("SEC004", "京沪高铁-K200+500至K205+900", "京沪高铁", 200.5, 205.9, "B002", "S001", "W004", "NORMAL"));
        sections.add(new CatenarySection("SEC005", "京沪高铁-K230+100至K235+400", "京沪高铁", 230.1, 235.4, "B002", "S002", "W005", "OFFLINE"));
        sections.add(new CatenarySection("SEC006", "京沪高铁-K260+800至K265+200", "京沪高铁", 260.8, 265.2, "B002", "S003", "W006", "WARNING"));
        log.info("Initialized {} demo sections", sections.size());
    }

    public List<SectionOverview> getAllSections() {
        return sections.stream()
                .map(this::toSectionOverview)
                .collect(Collectors.toList());
    }

    public List<SectionOverview> getSectionsByBureau(String bureauId) {
        return sections.stream()
                .filter(s -> s.getBureauId().equals(bureauId))
                .map(this::toSectionOverview)
                .collect(Collectors.toList());
    }

    public List<SectionOverview> getSectionsByStation(String stationId) {
        return sections.stream()
                .filter(s -> s.getStationId().equals(stationId))
                .map(this::toSectionOverview)
                .collect(Collectors.toList());
    }

    public List<SectionOverview> getSectionsByWorkArea(String workAreaId) {
        return sections.stream()
                .filter(s -> s.getWorkAreaId().equals(workAreaId))
                .map(this::toSectionOverview)
                .collect(Collectors.toList());
    }

    public SectionOverview getSectionDetail(String sectionId) {
        return sections.stream()
                .filter(s -> s.getSectionId().equals(sectionId))
                .findFirst()
                .map(this::toSectionOverview)
                .orElse(null);
    }

    private SectionOverview toSectionOverview(CatenarySection section) {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(3600);

        double latestIntensity = 0;
        double avgIntensity = 0;
        double maxIntensity = 0;
        int alarmCount = 0;

        try {
            avgIntensity = arcIntensityRepository.queryAvgIntensity(section.getSectionId(), start, end);
            maxIntensity = arcIntensityRepository.queryMaxIntensity(section.getSectionId(), start, end);
            latestIntensity = maxIntensity;
        } catch (Exception e) {
            log.debug("Failed to query stats for sectionId={}", section.getSectionId());
        }

        return SectionOverview.builder()
                .sectionId(section.getSectionId())
                .sectionName(section.getSectionName())
                .lineName(section.getLineName())
                .startKm(section.getStartKm())
                .endKm(section.getEndKm())
                .status(section.getStatus())
                .latestIntensity(latestIntensity)
                .avgIntensity(avgIntensity)
                .maxIntensity(maxIntensity)
                .alarmCount(alarmCount)
                .sensorCount(8)
                .build();
    }
}
