package com.catenary.arc.config;

import com.catenary.arc.websocket.ArcDataWebSocketHandler;
import com.catenary.arc.websocket.WebSocketHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ArcDataWebSocketHandler arcDataWebSocketHandler;
    private final WebSocketHandshakeHandler webSocketHandshakeHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(arcDataWebSocketHandler, "/ws/arc")
                .setAllowedOrigins("*")
                .setHandshakeHandler(webSocketHandshakeHandler);
    }
}
