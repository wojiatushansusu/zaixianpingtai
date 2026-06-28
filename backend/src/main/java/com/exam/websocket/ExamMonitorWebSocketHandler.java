package com.exam.websocket;

import com.exam.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExamMonitorWebSocketHandler extends BinaryWebSocketHandler {

    private static final Map<String, StudentSession> studentSessions = new ConcurrentHashMap<>();
    private static final Map<String, WebSocketSession> teacherSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtil jwtUtil;

    @Getter
    public static class StudentSession {
        private final WebSocketSession session;
        private final Long examId;
        private final Long studentId;

        public StudentSession(WebSocketSession session, Long examId, Long studentId) {
            this.session = session;
            this.examId = examId;
            this.studentId = studentId;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractToken(session);
        String role = extractParam(session, "role");

        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("监控WS认证失败，关闭连接: sessionId={}", session.getId());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if ("teacher".equals(role)) {
            teacherSessions.put(String.valueOf(userId), session);
            log.info("教师监控WS连接建立: teacherId={}", userId);
        } else {
            String examIdStr = extractParam(session, "examId");
            Long examId = examIdStr != null ? Long.parseLong(examIdStr) : null;
            if (examId != null) {
                String key = examId + "_" + userId;
                studentSessions.put(key, new StudentSession(session, examId, userId));
                log.info("学生监控WS连接建立: examId={}, studentId={}", examId, userId);
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        byte[] payload = message.getPayload().array();
        String token = extractToken(session);
        if (token == null || !jwtUtil.validateToken(token)) return;

        Long userId = jwtUtil.getUserId(token);
        String examIdStr = extractParam(session, "examId");
        Long examId = examIdStr != null ? Long.parseLong(examIdStr) : null;

        if (examId != null && userId != null) {
            broadcastToTeachers(examId, userId, payload);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            if ("ping".equals(payload)) {
                session.sendMessage(new TextMessage("pong"));
                return;
            }

            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
            String token = extractToken(session);
            if (token == null || !jwtUtil.validateToken(token)) return;

            Long userId = jwtUtil.getUserId(token);
            String examIdStr = extractParam(session, "examId");
            Long examId = examIdStr != null ? Long.parseLong(examIdStr) : null;

            if (examId != null && userId != null) {
                broadcastTextToTeachers(examId, userId, msg);
            }
        } catch (Exception e) {
            log.warn("处理监控WS文本消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("监控WS传输错误: sessionId={}", session.getId(), exception);
        removeSession(session);
    }

    private void removeSession(WebSocketSession session) {
        String sessionId = session.getId();

        studentSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getSession().getId().equals(sessionId)) {
                log.info("学生监控WS连接关闭: examId={}, studentId={}",
                        entry.getValue().getExamId(), entry.getValue().getStudentId());
                return true;
            }
            return false;
        });

        teacherSessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getId().equals(sessionId)) {
                log.info("教师监控WS连接关闭: teacherId={}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void broadcastToTeachers(Long examId, Long studentId, byte[] frameData) {
        Map<String, Object> msg = Map.of(
                "type", "frame",
                "examId", examId,
                "studentId", studentId,
                "timestamp", System.currentTimeMillis()
        );

        teacherSessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(frameData));
                } catch (IOException e) {
                    log.error("发送监控帧给教师失败", e);
                }
            }
        });
    }

    private void broadcastTextToTeachers(Long examId, Long studentId, Map<String, Object> data) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", data.getOrDefault("type", "event"),
                    "examId", examId,
                    "studentId", studentId,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(msg);

            teacherSessions.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        log.error("发送监控文本给教师失败", e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("广播监控文本失败", e);
        }
    }

    public void sendViolationAlert(Long examId, Long studentId, String violationType, String description) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", "violation",
                    "examId", examId,
                    "studentId", studentId,
                    "violationType", violationType,
                    "description", description,
                    "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(msg);

            teacherSessions.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(json));
                    } catch (IOException e) {
                        log.error("发送违规告警失败", e);
                    }
                }
            });
        } catch (Exception e) {
            log.error("发送违规告警失败", e);
        }
    }

    public void broadcastToExamRoom(Long examId, String type, Map<String, Object> data, String targetRole) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", type,
                    "examId", examId,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(msg);

            if ("teacher".equals(targetRole)) {
                teacherSessions.values().forEach(session -> {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(json));
                        } catch (IOException e) {
                            log.error("广播给教师失败", e);
                        }
                    }
                });
            } else if ("student".equals(targetRole)) {
                studentSessions.values().forEach(s -> {
                    if (s.getExamId().equals(examId) && s.getSession().isOpen()) {
                        try {
                            s.getSession().sendMessage(new TextMessage(json));
                        } catch (IOException e) {
                            log.error("广播给学生失败", e);
                        }
                    }
                });
            } else {
                teacherSessions.values().forEach(session -> {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(json));
                        } catch (IOException e) {
                            log.error("广播失败", e);
                        }
                    }
                });
                studentSessions.values().forEach(s -> {
                    if (s.getExamId().equals(examId) && s.getSession().isOpen()) {
                        try {
                            s.getSession().sendMessage(new TextMessage(json));
                        } catch (IOException e) {
                            log.error("广播失败", e);
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("广播考试房间消息失败", e);
        }
    }

    public void sendToUser(Long userId, String type, Map<String, Object> data) {
        try {
            Map<String, Object> msg = Map.of(
                    "type", type,
                    "data", data,
                    "timestamp", System.currentTimeMillis()
            );
            String json = objectMapper.writeValueAsString(msg);

            StudentSession studentSession = studentSessions.values().stream()
                    .filter(s -> s.getStudentId().equals(userId) && s.getSession().isOpen())
                    .findFirst()
                    .orElse(null);
            if (studentSession != null) {
                studentSession.getSession().sendMessage(new TextMessage(json));
                return;
            }

            WebSocketSession teacherSession = teacherSessions.get(String.valueOf(userId));
            if (teacherSession != null && teacherSession.isOpen()) {
                teacherSession.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("发送消息给用户失败: userId={}", userId, e);
        }
    }

    public int getOnlineStudentCount(Long examId) {
        return (int) studentSessions.values().stream()
                .filter(s -> s.getExamId().equals(examId) && s.getSession().isOpen())
                .count();
    }

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

    private String extractParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        String query = uri.getQuery();
        for (String param : query.split("&")) {
            if (param.startsWith(name + "=")) {
                return param.substring(name.length() + 1);
            }
        }
        return null;
    }
}
