package com.exam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.exam.websocket.NotificationWebSocketHandler;
import com.exam.websocket.ExamMonitorWebSocketHandler;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final ExamMonitorWebSocketHandler examMonitorWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 基础通知通道
        registry.addHandler(notificationWebSocketHandler, "/ws/notification")
                .setAllowedOrigins("*");
        
        // 考试监控通道（支持二进制帧传输）
        registry.addHandler(examMonitorWebSocketHandler, "/ws/exam-monitor")
                .setAllowedOrigins("*");
    }
}
