package com.catenary.arc.websocket;

import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class SessionMeta {

    private WebSocketSession session;
    private long createTime;
    private long lastActiveTime;
    private int missedPingCount;
    private String remoteIp;

    public SessionMeta(WebSocketSession session) {
        this.session = session;
        this.createTime = System.currentTimeMillis();
        this.lastActiveTime = System.currentTimeMillis();
        this.missedPingCount = 0;
        this.remoteIp = session.getRemoteAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
