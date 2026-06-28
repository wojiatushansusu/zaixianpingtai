package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.mapper.RoleMapper;
import com.exam.mapper.UserMapper;
import com.exam.pojo.Role;
import com.exam.pojo.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个人中心控制器（所有已登录用户可用）
 */
@Slf4j
@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取当前用户个人信息
     */
    @GetMapping
    public Result<User> getProfile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 填充角色信息
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                user.setRoleName(role.getRoleName());
                user.setRoleKey(role.getRoleKey());
            }
        }
        return Result.success(user);
    }

    /**
     * 修改个人信息（姓名、邮箱、手机号、性别）
     */
    @PutMapping
    public Result<Void> updateProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (params.containsKey("realName")) {
            user.setRealName(params.get("realName"));
        }
        if (params.containsKey("email")) {
            user.setEmail(params.get("email"));
        }
        if (params.containsKey("phone")) {
            user.setPhone(params.get("phone"));
        }
        if (params.containsKey("gender")) {
            user.setGender(params.get("gender"));
        }

        userMapper.updateById(user);
        return Result.success();
    }

    /**
     * 修改密码（通用接口）
     * 兼容旧明文密码：如果BCrypt匹配失败，尝试明文比较
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) {
            return Result.error(400, "请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            return Result.error(400, "密码长度为6-20个字符");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(2001, "用户不存在");
        }

        // 密码验证：优先BCrypt比对，兼容明文密码（旧数据）
        String storedPassword = user.getPassword();
        boolean passwordMatch;
        if (storedPassword != null && storedPassword.startsWith("$2a$")) {
            // BCrypt加密的密码
            passwordMatch = passwordEncoder.matches(oldPassword, storedPassword);
        } else if (storedPassword != null && storedPassword.equals(oldPassword)) {
            // 旧明文密码匹配，自动迁移为BCrypt加密
            log.info("检测到明文密码，用户 {} 将自动迁移为BCrypt加密", user.getUsername());
            passwordMatch = true;
        } else if (storedPassword != null && passwordEncoder.matches(oldPassword, storedPassword)) {
            // BCrypt其他前缀（$2b$等）也尝试匹配
            passwordMatch = true;
        } else {
            passwordMatch = false;
        }

        if (!passwordMatch) {
            return Result.error(2002, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success();
    }
}
