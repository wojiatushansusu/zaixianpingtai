package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.enums.ResultCodeEnum;
import com.exam.common.exception.BusinessException;
import com.exam.dto.RegisterDTO;
import com.exam.mapper.RoleMapper;
import com.exam.mapper.UserMapper;
import com.exam.pojo.Role;
import com.exam.pojo.User;
import com.exam.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 学生注册
     */
    public void register(RegisterDTO registerDTO) {
        // 检查用户名是否存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, registerDTO.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCodeEnum.USERNAME_EXISTS);
        }

        // 默认注册为学生角色
        Role studentRole = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleKey, "student"));
        if (studentRole == null) {
            throw new BusinessException("系统未配置学生角色");
        }

        // 创建用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setRoleId(studentRole.getId());
        user.setStatus(1);

        userMapper.insert(user);
    }

    /**
     * 获取用户信息
     */
    public Map<String, Object> getUserInfo(Long userId) {
        User user = userMapper.selectUserWithRole(userId);
        if (user == null) {
            throw new BusinessException(ResultCodeEnum.USER_NOT_FOUND);
        }

        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("avatar", user.getAvatar());
        info.put("roleKey", user.getRoleKey());
        info.put("roleName", user.getRoleName());
        info.put("roleId", user.getRoleId());
        return info;
    }

    /**
     * 根据用户名查询用户（用于密码重置等场景）
     */
    public User getUserByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 更新用户密码（用于密码重置）
     */
    public void updateUserPassword(User user) {
        userMapper.updateById(user);
    }
}
