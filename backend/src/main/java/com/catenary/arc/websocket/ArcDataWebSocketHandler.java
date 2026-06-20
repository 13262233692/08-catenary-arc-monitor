package com.catenary.arc.websocket;

import com.catenary.arc.dto.ArcDataPoint;
import com.catenary.arc.service.KafkaConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArcDataWebSocketHandler extends TextWebSocketHandler {

    private static final int MAX_CONNECTIONS = 1000;
    private static final int MAX_MISSED_PINGS = 3;
    private static final long IDLE_TIMEOUT_MS = 90000;

    private final KafkaConsumerService kafkaConsumerService;

    private final ConcurrentHashMap<String, SessionMeta> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sectionSubscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalClosedConnections = new AtomicInteger(0);

    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (activeConnections.get() >= MAX_CONNECTIONS) {
            log.warn("Connection limit reached, rejecting session {}", session.getId());
            session.close(CloseStatus.VIOLATED_POLICY);
            return;
        }
        SessionMeta meta = new SessionMeta(session);
        sessions.put(session.getId(), meta);
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        log.info("WebSocket connected: {}, remoteIp: {}, active: {}",
                session.getId(), meta.getRemoteIp(), activeConnections.get());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        sectionSubscriptions.values().forEach(ids -> ids.remove(sessionId));
        sectionSubscriptions.entrySet().removeIf(e -> e.getValue().isEmpty());
        activeConnections.decrementAndGet();
        totalClosedConnections.incrementAndGet();
        log.info("WebSocket disconnected: {}, status: {}, active: {}",
                sessionId, status, activeConnections.get());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionMeta meta = sessions.get(session.getId());
        if (meta == null) {
            return;
        }
        meta.setLastActiveTime(System.currentTimeMillis());
        try {
            JsonNode json = objectMapper.readTree(message.getPayload());
            String type = json.has("type") ? json.get("type").asText() : "";
            if ("pong".equals(type)) {
                meta.setMissedPingCount(0);
                meta.setLastActiveTime(System.currentTimeMillis());
                return;
            }
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

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        SessionMeta meta = sessions.get(session.getId());
        if (meta != null) {
            meta.setMissedPingCount(0);
            meta.setLastActiveTime(System.currentTimeMillis());
        }
    }

    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        if (sessions.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        String pingPayload = "{\"type\":\"ping\",\"timestamp\":" + timestamp + "}";
        TextMessage pingMessage = new TextMessage(pingPayload);
        for (SessionMeta meta : sessions.values()) {
            WebSocketSession session = meta.getSession();
            if (session.isOpen()) {
                try {
                    session.sendMessage(pingMessage);
                    meta.setMissedPingCount(meta.getMissedPingCount() + 1);
                } catch (IOException e) {
                    log.error("Failed to send ping to session {}: {}", session.getId(), e.getMessage());
                    meta.setMissedPingCount(meta.getMissedPingCount() + 1);
                }
            }
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanDeadSessions() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> deadSessions = new ArrayList<>();
        for (Map.Entry<String, SessionMeta> entry : sessions.entrySet()) {
            SessionMeta meta = entry.getValue();
            boolean isDead = meta.getMissedPingCount() >= MAX_MISSED_PINGS
                    || (now - meta.getLastActiveTime()) > IDLE_TIMEOUT_MS;
            if (isDead) {
                deadSessions.add(entry.getKey());
            }
        }
        for (String sessionId : deadSessions) {
            SessionMeta meta = sessions.get(sessionId);
            if (meta != null) {
                try {
                    if (meta.getSession().isOpen()) {
                        meta.getSession().close(CloseStatus.SESSION_NOT_RELIABLE);
                    }
                } catch (IOException e) {
                    log.error("Error closing dead session {}: {}", sessionId, e.getMessage());
                }
                sessions.remove(sessionId);
                sectionSubscriptions.values().forEach(ids -> ids.remove(sessionId));
                sectionSubscriptions.entrySet().removeIf(e -> e.getValue().isEmpty());
                activeConnections.decrementAndGet();
                totalClosedConnections.incrementAndGet();
                log.warn("Cleaned up dead session: {}, remoteIp: {}, missedPings: {}, idleMs: {}",
                        sessionId, meta.getRemoteIp(),
                        meta.getMissedPingCount(), now - meta.getLastActiveTime());
            }
        }
    }

    @Scheduled(fixedRate = 100)
    public void pushData() {
        if (sectionSubscriptions.isEmpty()) {
            return;
        }
        try {
            List<ArcDataPoint> dataPoints = kafkaConsumerService.getLatestData();
            if (dataPoints == null || dataPoints.isEmpty()) {
                return;
            }
            Map<String, List<ArcDataPoint>> sectionDataMap = new HashMap<>();
            for (ArcDataPoint point : dataPoints) {
                String sectionId = point.getSectionId();
                if (sectionSubscriptions.containsKey(sectionId)) {
                    sectionDataMap.computeIfAbsent(sectionId, k -> new ArrayList<>()).add(point);
                }
            }
            for (Map.Entry<String, List<ArcDataPoint>> entry : sectionDataMap.entrySet()) {
                String sectionId = entry.getKey();
                List<ArcDataPoint> sectionData = entry.getValue();
                Set<String> subscriberIds = sectionSubscriptions.get(sectionId);
                if (subscriberIds == null || subscriberIds.isEmpty()) {
                    continue;
                }
                String cacheKey = sectionId + ":" + sectionData.hashCode();
                String payload = messageCache.computeIfAbsent(cacheKey, k -> {
                    try {
                        return objectMapper.writeValueAsString(sectionData);
                    } catch (Exception e) {
                        log.error("Failed to serialize data for section {}: {}", sectionId, e.getMessage());
                        return null;
                    }
                });
                if (payload == null) {
                    continue;
                }
                TextMessage message = new TextMessage(payload);
                for (String sessionId : subscriberIds) {
                    SessionMeta meta = sessions.get(sessionId);
                    if (meta == null || !meta.getSession().isOpen()) {
                        continue;
                    }
                    try {
                        meta.getSession().sendMessage(message);
                    } catch (IOException e) {
                        log.error("Transport error for session {}: {}", sessionId, e.getMessage());
                        meta.setMissedPingCount(meta.getMissedPingCount() + 1);
                    }
                }
            }
            if (messageCache.size() > 1000) {
                messageCache.clear();
            }
        } catch (Exception e) {
            log.error("Error pushing data: {}", e.getMessage());
        }
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConnections", totalConnections.get());
        stats.put("activeConnections", activeConnections.get());
        stats.put("totalClosedConnections", totalClosedConnections.get());
        return stats;
    }

    public int getActiveSessionCount() {
        return activeConnections.get();
    }
}
