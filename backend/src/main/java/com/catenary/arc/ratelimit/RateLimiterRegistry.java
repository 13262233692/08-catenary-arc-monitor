package com.catenary.arc.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimiterRegistry {

    private final ConcurrentHashMap<String, SlidingWindowRateLimiter> limiters = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter getOrCreate(String key, int maxRequests, long windowMillis) {
        return limiters.computeIfAbsent(key, k -> new SlidingWindowRateLimiter(maxRequests, windowMillis));
    }

    public void remove(String key) {
        limiters.remove(key);
    }

    public Map<String, Object> getAllStats() {
        Map<String, Object> stats = new HashMap<>();
        for (Map.Entry<String, SlidingWindowRateLimiter> entry : limiters.entrySet()) {
            Map<String, Object> limiterStats = new HashMap<>();
            SlidingWindowRateLimiter limiter = entry.getValue();
            limiterStats.put("remainingRequests", limiter.getRemainingRequests());
            limiterStats.put("windowResetTimeMs", limiter.getWindowResetTime());
            stats.put(entry.getKey(), limiterStats);
        }
        return stats;
    }
}
