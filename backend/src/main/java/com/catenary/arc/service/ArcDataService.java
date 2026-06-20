package com.catenary.arc.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import com.catenary.arc.dto.ArcDataPoint;
import com.catenary.arc.dto.ArcDataQueryRequest;
import com.catenary.arc.entity.ArcIntensity;
import com.catenary.arc.repository.ArcIntensityRepository;
import com.catenary.arc.util.LttbDownsampler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArcDataService {

    private static final long MAX_TIME_RANGE_MS = 86400000;
    private static final int MAX_POINTS_LIMIT = 5000;

    private final ArcIntensityRepository arcIntensityRepository;

    private final AtomicLong totalQueryCount = new AtomicLong(0);
    private final AtomicLong totalPointsReturned = new AtomicLong(0);
    private final AtomicLong lttbDownsampleCount = new AtomicLong(0);
    private final AtomicLong totalQueryTimeMs = new AtomicLong(0);

    public List<ArcDataPoint> queryArcData(ArcDataQueryRequest request) {
        long startTime = System.currentTimeMillis();

        long timeRangeMs = request.getEndMs() - request.getStartMs();
        if (timeRangeMs > MAX_TIME_RANGE_MS) {
            throw new IllegalArgumentException("Time range too large, maximum is 24 hours");
        }

        int requestedMaxPoints = Math.min(request.getMaxPoints(), MAX_POINTS_LIMIT);
        int adaptiveTarget = Math.min(requestedMaxPoints * 2, MAX_POINTS_LIMIT);

        Instant start = Instant.ofEpochMilli(request.getStartMs());
        Instant end = Instant.ofEpochMilli(request.getEndMs());

        List<com.catenary.arc.repository.ArcDataPoint> repoPoints =
                arcIntensityRepository.queryAdaptiveDownsampled(
                        request.getSectionId(), start, end, adaptiveTarget);

        if (repoPoints.size() > requestedMaxPoints) {
            double[][] dataArray = new double[repoPoints.size()][2];
            for (int i = 0; i < repoPoints.size(); i++) {
                com.catenary.arc.repository.ArcDataPoint p = repoPoints.get(i);
                dataArray[i][0] = p.getTime().toEpochMilli();
                dataArray[i][1] = p.getIntensity();
            }

            List<double[]> downsampled = LttbDownsampler.lttb(dataArray, requestedMaxPoints);
            lttbDownsampleCount.incrementAndGet();

            List<ArcDataPoint> result = downsampled.stream()
                    .map(p -> ArcDataPoint.builder()
                            .timestamp((long) p[0])
                            .sectionId(request.getSectionId())
                            .intensity(p[1])
                            .build())
                    .collect(Collectors.toList());

            totalQueryCount.incrementAndGet();
            totalPointsReturned.addAndGet(result.size());
            totalQueryTimeMs.addAndGet(System.currentTimeMillis() - startTime);

            return result;
        }

        List<ArcDataPoint> result = repoPoints.stream()
                .map(p -> ArcDataPoint.builder()
                        .timestamp(p.getTime().toEpochMilli())
                        .sectionId(request.getSectionId())
                        .intensity(p.getIntensity())
                        .build())
                .collect(Collectors.toList());

        totalQueryCount.incrementAndGet();
        totalPointsReturned.addAndGet(result.size());
        totalQueryTimeMs.addAndGet(System.currentTimeMillis() - startTime);

        return result;
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

    public Map<String, Object> getQueryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueryCount", totalQueryCount.get());
        stats.put("totalPointsReturned", totalPointsReturned.get());
        stats.put("lttbDownsampleCount", lttbDownsampleCount.get());
        stats.put("totalQueryTimeMs", totalQueryTimeMs.get());
        long avgTimeMs = totalQueryCount.get() > 0
                ? totalQueryTimeMs.get() / totalQueryCount.get()
                : 0;
        stats.put("avgQueryTimeMs", avgTimeMs);
        return stats;
    }
}
