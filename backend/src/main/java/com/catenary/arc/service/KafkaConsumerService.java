package com.catenary.arc.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.catenary.arc.dto.ArcDataPoint;
import com.catenary.arc.entity.ArcIntensity;
import com.catenary.arc.repository.ArcIntensityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ArcIntensityRepository arcIntensityRepository;
    private final ObjectMapper objectMapper;

    private final BlockingQueue<ArcDataPoint> recentDataQueue = new LinkedBlockingQueue<>(10000);

    @KafkaListener(topics = "arc-intensity-data", groupId = "arc-monitor-group")
    public void consumeArcData(ConsumerRecord<String, String> record) {
        try {
            ArcDataPoint dataPoint = objectMapper.readValue(record.value(), ArcDataPoint.class);

            ArcIntensity arcIntensity = ArcIntensity.builder()
                    .time(Instant.ofEpochMilli(dataPoint.getTimestamp()))
                    .sectionId(dataPoint.getSectionId())
                    .sensorId(dataPoint.getSensorId())
                    .intensity(dataPoint.getIntensity())
                    .voltage(dataPoint.getVoltage())
                    .current(dataPoint.getCurrent())
                    .temperature(dataPoint.getTemperature())
                    .build();

            arcIntensityRepository.write(arcIntensity);

            if (!recentDataQueue.offer(dataPoint)) {
                recentDataQueue.poll();
                recentDataQueue.offer(dataPoint);
            }

            log.debug("Consumed arc data from Kafka, sectionId={}, offset={}",
                    dataPoint.getSectionId(), record.offset());
        } catch (Exception e) {
            log.error("Failed to process arc data from Kafka, offset={}", record.offset(), e);
        }
    }

    public List<ArcDataPoint> getLatestData() {
        List<ArcDataPoint> data = new ArrayList<>();
        recentDataQueue.drainTo(data);
        return data;
    }
}
