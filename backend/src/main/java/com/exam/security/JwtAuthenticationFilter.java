package com.exam.security;

import com.exam.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 从请求头获取Token
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token)) {
            try {
                // 检查Token是否在黑名单中（已登出）
                Boolean isBlacklisted = redisTemplate.hasKey("token:blacklist:" + token);
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    // Token已失效，清除前端可能残留的信息
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\"}");
                    return;
                }

                // 检查Redis中是否存在该用户的合法Token（对比当前Token）
                // 验证Token
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserId(token);
                    String username = jwtUtil.getUsername(token);
                    String roleKey = jwtUtil.getRoleKey(token);

                    // 验证Redis中存储的Token是否与当前Token一致（防止旧Token继续使用）
                    String tokenKey = "token:user:" + userId;
                    String cachedToken = redisTemplate.opsForValue().get(tokenKey);
                    if (cachedToken != null && !cachedToken.equals(token)) {
                        // Token已被替换（用户重新登录），当前Token失效
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":401,\"message\":\"账号已在其他地方登录\"}");
                        return;
                    }

                    // 构建认证对象，携带角色权限（与SecurityConfig的hasAuthority规则匹配）
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + roleKey)
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    // 将userId和roleKey放入请求属性
                    request.setAttribute("userId", userId);
                    request.setAttribute("roleKey", roleKey);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.warn("JWT认证失败: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
