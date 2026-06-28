package com.exam.security;

import com.exam.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * JWT 登出处理器 - 将Token加入黑名单
 */
@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        String token = getTokenFromRequest(request);
        if (token != null && jwtUtil.validateToken(token)) {
            // 将Token加入Redis黑名单，设置过期时间与Token一致
            String username = jwtUtil.getUsername(token);
            redisTemplate.opsForValue().set("token:blacklist:" + token, "1", 24, TimeUnit.HOURS);
        }
        SecurityContextHolder.clearContext();
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
