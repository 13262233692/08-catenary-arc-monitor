package com.catenary.arc.websocket;

import com.catenary.arc.service.KafkaConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArcDataWebSocketHandler extends TextWebSocketHandler {

    private final KafkaConsumerService kafkaConsumerService;

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private final Map<String, Set<String>> sectionSubscriptions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("WebSocket connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sectionSubscriptions.values().forEach(ids -> ids.remove(session.getId()));
        sectionSubscriptions.entrySet().removeIf(e -> e.getValue().isEmpty());
        log.info("WebSocket disconnected: {}, status: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String action = json.has("action") ? json.get("action").asText() : "";
            String sectionId = json.has("sectionId") ? json.get("sectionId").asText() : "";

            switch (action) {
                case "subscribe" -> {
                    sectionSubscriptions.computeIfAbsent(sectionId, k -> ConcurrentHashMap.newKeySet())
                            .add(session.getId());
                    log.info("Session {} subscribed to section {}", session.getId(), sectionId);
                }
                case "unsubscribe" -> {
                    Set<String> subscribers = sectionSubscriptions.get(sectionId);
                    if (subscribers != null) {
                        subscribers.remove(session.getId());
                        if (subscribers.isEmpty()) {
                            sectionSubscriptions.remove(sectionId);
                        }
                    }
                    log.info("Session {} unsubscribed from section {}", session.getId(), sectionId);
                }
                default -> log.warn("Unknown action '{}' from session {}", action, session.getId());
            }
        } catch (Exception e) {
            log.error("Failed to parse message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 100)
    public void pushData() {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            Object data = kafkaConsumerService.getLatestData();
            if (data == null) {
                return;
            }
            String payload = objectMapper.writeValueAsString(data);
            TextMessage message = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Transport error for session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error pushing data: {}", e.getMessage());
        }
    }
}
