package com.catenary.arc.controller;

import com.catenary.arc.dto.ArcDataPoint;
import com.catenary.arc.dto.ArcDataQueryRequest;
import com.catenary.arc.dto.ApiResponse;
import com.catenary.arc.service.ArcDataService;
import com.catenary.arc.service.KafkaProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/arc-data")
@RequiredArgsConstructor
public class ArcDataController {

    private final KafkaProducerService kafkaProducerService;
    private final ArcDataService arcDataService;

    @PostMapping("/ingest")
    public ApiResponse<String> ingest(@RequestBody ArcDataPoint dataPoint) {
        kafkaProducerService.sendArcData(dataPoint);
        return ApiResponse.success("Data sent to Kafka");
    }

    @PostMapping("/ingest-batch")
    public ApiResponse<String> ingestBatch(@RequestBody List<ArcDataPoint> dataPoints) {
        kafkaProducerService.sendArcDataBatch(dataPoints);
        return ApiResponse.success("Batch data sent to Kafka");
    }

    @PostMapping("/query")
    public ApiResponse<Object> query(@RequestBody @Valid ArcDataQueryRequest queryRequest) {
        Object data = arcDataService.queryArcData(queryRequest);
        return ApiResponse.success(data);
    }

    @GetMapping("/latest/{sectionId}")
    public ApiResponse<ArcDataPoint> getLatest(@PathVariable String sectionId) {
        ArcDataPoint dataPoint = arcDataService.getLatestDataPoint(sectionId);
        return ApiResponse.success(dataPoint);
    }

    @GetMapping("/raw")
    public ApiResponse<Object> getRawData(
            @RequestParam String sectionId,
            @RequestParam long startMs,
            @RequestParam long endMs) {
        Object data = arcDataService.queryRawData(sectionId, startMs, endMs);
        return ApiResponse.success(data);
    }
}
