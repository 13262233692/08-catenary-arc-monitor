package com.catenary.arc.interceptor;

import com.catenary.arc.dto.ApiResponse;
import com.catenary.arc.ratelimit.RateLimiterRegistry;
import com.catenary.arc.ratelimit.SlidingWindowRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/arc-data/query")) {
            return true;
        }

        String clientIp = getClientIp(request);
        String key = "query:arc-data:" + clientIp;

        SlidingWindowRateLimiter limiter = rateLimiterRegistry.getOrCreate(key, 60, 60000);

        if (!limiter.tryAcquire()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Object> apiResponse = ApiResponse.error(429, "Rate limit exceeded. Try again later.");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            log.warn("Rate limit exceeded for IP: {}, key: {}", clientIp, key);
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            int commaIndex = xff.indexOf(',');
            if (commaIndex > 0) {
                return xff.substring(0, commaIndex).trim();
            }
            return xff.trim();
        }
        return request.getRemoteAddr();
    }
}
