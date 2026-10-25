package com.exam.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtLogoutHandler jwtLogoutHandler;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭CSRF（前后端分离不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 开启CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 无状态Session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 请求授权
            .authorizeHttpRequests(auth -> auth
                // 放行接口
                // 注意：/files/** 与 /uploads/** 曾被放行，但 WebMvcConfig 把 /files/** 映射到了
                // 本地 uploads 目录，而监考视频证据、人脸图片正好落在该目录下——等于任何人
                // 拿到 URL 就能直接下载考生的影像资料。这两个前缀已移出白名单，改为需登录访问。
                // 头像走的是 FileController 的 /files/avatar/me（数据库存储），不受影响。
                .requestMatchers(
                    "/auth/login",
                    "/auth/register",
                    "/auth/logout",
                    "/ws/**",
                    "/ai-assistant/status",
                    "/auth/reset-password/**"
                ).permitAll()
                // 管理员接口
                .requestMatchers("/admin/**").hasAuthority("ROLE_admin")
                // 教师接口
                .requestMatchers("/teacher/**").hasAnyAuthority("ROLE_teacher", "ROLE_admin")
                // 学生接口
                .requestMatchers("/student/**").hasAnyAuthority("ROLE_student", "ROLE_admin")
                // BUG-19修复：AI助手接口需要认证（任意已登录用户可访问），显式声明避免落入 anyRequest
                .requestMatchers("/ai-assistant/**").authenticated()
                // 通用认证接口
                .requestMatchers("/user/**", "/common/**").authenticated()
                // 其他请求
                .anyRequest().authenticated()
            )
            // 异常处理
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            // 登出处理
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .addLogoutHandler(jwtLogoutHandler)
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":200,\"message\":\"登出成功\"}");
                })
            )
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        } else {
            configuration.setAllowedOriginPatterns(List.of("http://localhost:*,http://127.0.0.1:*"));
        }
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
