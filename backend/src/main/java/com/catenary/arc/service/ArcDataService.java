package com.catenary.arc.service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.catenary.arc.dto.ArcDataPoint;
import com.catenary.arc.dto.ArcDataQueryRequest;
import com.catenary.arc.entity.ArcIntensity;
import com.catenary.arc.repository.ArcIntensityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArcDataService {

    private final ArcIntensityRepository arcIntensityRepository;

    public List<ArcDataPoint> queryArcData(ArcDataQueryRequest request) {
        Instant start = Instant.ofEpochMilli(request.getStartMs());
        Instant end = Instant.ofEpochMilli(request.getEndMs());

        return arcIntensityRepository.queryDownsampled(
                        request.getSectionId(), start, end,
                        request.getInterval(), request.getMaxPoints()).stream()
                .map(p -> ArcDataPoint.builder()
                        .timestamp(p.getTime().toEpochMilli())
                        .sectionId(request.getSectionId())
                        .intensity(p.getIntensity())
                        .build())
                .collect(Collectors.toList());
    }

    public List<ArcIntensity> queryRawData(String sectionId, Instant start, Instant end) {
        return arcIntensityRepository.queryBySectionAndTimeRange(sectionId, start, end);
    }

    public ArcDataPoint getLatestDataPoint(String sectionId) {
        Instant end = Instant.now();
        Instant start = end.minusSeconds(300);

        List<com.catenary.arc.repository.ArcDataPoint> points =
                arcIntensityRepository.queryDownsampled(sectionId, start, end, "1s", 1);

        if (points.isEmpty()) {
            return null;
        }

        com.catenary.arc.repository.ArcDataPoint latest = points.get(0);
        return ArcDataPoint.builder()
                .timestamp(latest.getTime().toEpochMilli())
                .sectionId(sectionId)
                .intensity(latest.getIntensity())
                .build();
    }
}
