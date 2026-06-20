package com.catenary.arc.service;

import java.util.List;
import com.catenary.arc.dto.ArcDataPoint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String TOPIC = "arc-intensity-data";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendArcData(ArcDataPoint dataPoint) {
        try {
            String json = objectMapper.writeValueAsString(dataPoint);
            kafkaTemplate.send(TOPIC, dataPoint.getSectionId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send arc data to Kafka, sectionId={}", dataPoint.getSectionId(), ex);
                        } else {
                            log.debug("Sent arc data to Kafka, sectionId={}, offset={}",
                                    dataPoint.getSectionId(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ArcDataPoint, sectionId={}", dataPoint.getSectionId(), e);
        }
    }

    public void sendArcDataBatch(List<ArcDataPoint> dataPoints) {
        for (ArcDataPoint dataPoint : dataPoints) {
            sendArcData(dataPoint);
        }
    }
}
