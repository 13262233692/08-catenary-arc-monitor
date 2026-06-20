package com.catenary.arc.repository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import com.catenary.arc.entity.ArcIntensity;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.query.FluxTable;
import com.influxdb.client.write.Point;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class ArcIntensityRepository {

    private final InfluxDBClient influxDBClient;
    private final String bucket;
    private final String org;

    public ArcIntensityRepository(InfluxDBClient influxDBClient,
                                  @Value("${influxdb.bucket}") String bucket,
                                  @Value("${influxdb.org}") String org) {
        this.influxDBClient = influxDBClient;
        this.bucket = bucket;
        this.org = org;
    }

    public void write(ArcIntensity arcIntensity) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        Point point = toPoint(arcIntensity);
        writeApi.writePoint(bucket, org, point);
        log.debug("Written arc intensity point for sectionId={}", arcIntensity.getSectionId());
    }

    public void writeBatch(List<ArcIntensity> points) {
        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        List<Point> influxPoints = points.stream()
                .map(this::toPoint)
                .collect(Collectors.toList());
        writeApi.writePoints(bucket, org, influxPoints);
        log.debug("Written batch of {} arc intensity points", points.size());
    }

    public List<ArcIntensity> queryBySectionAndTimeRange(String sectionId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> pivot(rowKey: [\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
                bucket, start.toString(), end.toString(), sectionId);

        log.debug("Executing Flux query for sectionId={} range={} to {}", sectionId, start, end);
        return influxDBClient.getQueryApi().query(flux, ArcIntensity.class);
    }

    public List<ArcDataPoint> queryDownsampled(String sectionId, Instant start, Instant end,
                                                String interval, int maxPoints) {
        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> filter(fn: (r) => r._field == \"intensity\")" +
                "  |> aggregateWindow(every: %s, fn: mean, createEmpty: false)" +
                "  |> limit(n: %d)",
                bucket, start.toString(), end.toString(), sectionId, interval, maxPoints);

        log.debug("Executing downsampled query for sectionId={} interval={} maxPoints={}", sectionId, interval, maxPoints);
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> ArcDataPoint.builder()
                        .time(record.getTime())
                        .intensity(((Number) record.getValue()).doubleValue())
                        .build())
                .collect(Collectors.toList());
    }

    public double queryMaxIntensity(String sectionId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> filter(fn: (r) => r._field == \"intensity\")" +
                "  |> max()",
                bucket, start.toString(), end.toString(), sectionId);

        log.debug("Executing max intensity query for sectionId={}", sectionId);
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        return extractScalarValue(tables);
    }

    public double queryAvgIntensity(String sectionId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> filter(fn: (r) => r._field == \"intensity\")" +
                "  |> mean()",
                bucket, start.toString(), end.toString(), sectionId);

        log.debug("Executing avg intensity query for sectionId={}", sectionId);
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        return extractScalarValue(tables);
    }

    private Point toPoint(ArcIntensity arc) {
        return Point.measurement("arc_intensity")
                .time(arc.getTime(), WritePrecision.MS)
                .addTag("sectionId", arc.getSectionId())
                .addTag("sensorId", arc.getSensorId())
                .addField("intensity", arc.getIntensity())
                .addField("voltage", arc.getVoltage())
                .addField("current", arc.getCurrent())
                .addField("temperature", arc.getTemperature());
    }

    public List<ArcDataPoint> queryAdaptiveDownsampled(String sectionId, Instant start, Instant end, int targetPoints) {
        long timeRangeMs = end.toEpochMilli() - start.toEpochMilli();
        long intervalMs = Math.max(1, timeRangeMs / targetPoints);
        String interval = intervalMs + "ms";

        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> filter(fn: (r) => r._field == \"intensity\")" +
                "  |> aggregateWindow(every: %s, fn: mean, createEmpty: false)" +
                "  |> limit(n: %d)",
                bucket, start.toString(), end.toString(), sectionId, interval, targetPoints);

        log.debug("Executing adaptive downsampled query for sectionId={} interval={} targetPoints={}", sectionId, interval, targetPoints);
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        return tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> ArcDataPoint.builder()
                        .time(record.getTime())
                        .intensity(((Number) record.getValue()).doubleValue())
                        .build())
                .collect(Collectors.toList());
    }

    public long queryEstimatedPointCount(String sectionId, Instant start, Instant end) {
        String flux = String.format(
                "from(bucket: \"%s\")" +
                "  |> range(start: %s, stop: %s)" +
                "  |> filter(fn: (r) => r._measurement == \"arc_intensity\")" +
                "  |> filter(fn: (r) => r.sectionId == \"%s\")" +
                "  |> filter(fn: (r) => r._field == \"intensity\")" +
                "  |> count()",
                bucket, start.toString(), end.toString(), sectionId);

        log.debug("Executing point count query for sectionId={} range={} to {}", sectionId, start, end);
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux);
        if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
            return 0;
        }
        Object value = tables.get(0).getRecords().get(0).getValue();
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    private double extractScalarValue(List<FluxTable> tables) {
        if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
            return 0.0;
        }
        Object value = tables.get(0).getRecords().get(0).getValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
