package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.result.Result;
import com.exam.dto.LoginDTO;
import com.exam.dto.RegisterDTO;
import com.exam.pojo.User;
import com.exam.security.LoginUser;
import com.exam.util.JwtUtil;
import com.exam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO) {
        // 1. 认证
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword()));

        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        User user = loginUser.getUser();

        // 2. 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRoleKey());

        // 3. 将用户信息和Token存入Redis
        String tokenKey = "token:user:" + user.getId();
        redisTemplate.opsForValue().set(tokenKey, token, 24, TimeUnit.HOURS);

        // 4. 返回Token和用户信息
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("roleKey", user.getRoleKey());
        data.put("roleName", user.getRoleName());
        data.put("avatar", user.getAvatar());

        return Result.success(data);
    }

    /**
     * 用户注册（学生自注册）
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        authService.register(registerDTO);
        return Result.success();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> userInfo = authService.getUserInfo(userId);
        return Result.success(userInfo);
    }

    // ==================== 密码重置功能 ====================

    /**
     * 发送重置密码验证码（通过Redis存储验证码，开发模式直接返回验证码）
     * POST /auth/reset-password/request
     * 请求体: { "username": "xxx" }
     */
    @PostMapping("/reset-password/request")
    public Result<Map<String, Object>> requestResetPassword(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        if (username == null || username.isBlank()) {
            return Result.error(400, "请输入用户名");
        }

        // 查询用户是否存在
        User user = authService.getUserByUsername(username);
        if (user == null) {
            // 安全考虑：不暴露用户是否存在，返回统一提示
            return Result.success(Map.of("message", "如果用户名正确，验证码将发送到系统"));
        }

        // 生成6位数字验证码
        String code = String.valueOf(100000 + new java.util.Random().nextInt(900000));
        String redisKey = "reset:pwd:" + username;

        // 存入Redis，有效期10分钟
        redisTemplate.opsForValue().set(redisKey, code, 10, TimeUnit.MINUTES);

        log.info("[密码重置] 用户 {} 申请重置密码，验证码: {}", username, code);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "验证码已生成，如果用户名正确，验证码将发送到您的系统邮箱");
        result.put("expireSeconds", 600);

        return Result.success(result);
    }

    /**
     * 验证码校验 + 重置密码
     * POST /auth/reset-password/reset
     * 请求体: { "username": "xxx", "code": "123456", "newPassword": "新密码" }
     */
    @PostMapping("/reset-password/reset")
    public Result<Void> resetPassword(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String code = params.get("code");
        String newPassword = params.get("newPassword");

        if (username == null || username.isBlank()) {
            return Result.error(400, "请输入用户名");
        }
        if (code == null || code.isBlank()) {
            return Result.error(400, "请输入验证码");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.error(400, "密码长度为6-20个字符");
        }

        // 验证验证码
        String redisKey = "reset:pwd:" + username;
        String storedCode = redisTemplate.opsForValue().get(redisKey);

        if (storedCode == null) {
            return Result.error(400, "验证码已过期或未发送，请重新获取");
        }
        if (!storedCode.equals(code)) {
            return Result.error(400, "验证码错误");
        }

        // 查询用户
        User user = authService.getUserByUsername(username);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }

        // 重置密码
        user.setPassword(passwordEncoder.encode(newPassword));
        authService.updateUserPassword(user);

        // 删除已使用的验证码
        redisTemplate.delete(redisKey);

        log.info("[密码重置] 用户 {} 成功重置密码", username);
        return Result.success();
    }

}
