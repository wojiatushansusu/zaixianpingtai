package com.exam.websocket;

import com.exam.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础WebSocket通知处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtil jwtUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从URI参数中提取JWT token并验证
        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("通知WS认证失败，关闭连接: sessionId={}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId != null) {
            sessions.put(String.valueOf(userId), session);
            log.info("WebSocket连接建立: userId={}", userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 反向查找userId: 遍历sessions找到匹配的session
        String userIdToRemove = null;
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (entry.getValue().getId().equals(session.getId())) {
                userIdToRemove = entry.getKey();
                break;
            }
        }
        if (userIdToRemove != null) {
            sessions.remove(userIdToRemove);
            log.info("WebSocket连接关闭: userId={}", userIdToRemove);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 心跳检测
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // 反向查找userId
        String userIdToRemove = null;
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (entry.getValue().getId().equals(session.getId())) {
                userIdToRemove = entry.getKey();
                break;
            }
        }
        if (userIdToRemove != null) {
            sessions.remove(userIdToRemove);
            log.error("WebSocket传输错误: userId={}", userIdToRemove, exception);
        }
    }

    /**
     * 向指定用户发送通知
     */
    public void sendToUser(Long userId, String type, String content) {
        WebSocketSession session = sessions.get(String.valueOf(userId));
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> msg = Map.of(
                        "type", type,
                        "content", content,
                        "timestamp", System.currentTimeMillis()
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            } catch (IOException e) {
                log.error("发送WebSocket消息失败: userId={}", userId, e);
            }
        }
    }

    /**
     * 广播消息给所有用户
     */
    public void broadcast(String type, String content) {
        Map<String, Object> msg = Map.of(
                "type", type,
                "content", content,
                "timestamp", System.currentTimeMillis()
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            return;
        }

        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.error("广播消息失败", e);
                }
            }
        });
    }

    /**
     * 从WebSocket连接URI中提取JWT token
     */
    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
