package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.result.Result;
import com.exam.dto.UserDTO;
import com.exam.mapper.RoleMapper;
import com.exam.mapper.UserMapper;
import com.exam.pojo.Role;
import com.exam.pojo.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器（管理员）
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<Page<User>> listUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer status) {

        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), User::getUsername, username);
        wrapper.eq(roleId != null, User::getRoleId, roleId);
        wrapper.eq(status != null, User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> result = userMapper.selectPage(page, wrapper);

        // 填充角色信息
        result.getRecords().forEach(this::fillRoleInfo);

        return Result.success(result);
    }

    /**
     * 根据ID获取用户
     */
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        User user = userMapper.selectUserWithRole(id);
        return Result.success(user);
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Result<Void> createUser(@Valid @RequestBody UserDTO userDTO, HttpServletRequest request) {
        if (userDTO.getPassword() == null || userDTO.getPassword().length() < 6) {
            return Result.error(2002, "密码长度不能少于6个字符");
        }

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, userDTO.getUsername()));
        if (count > 0) {
            return Result.error(2001, "用户名已存在");
        }

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRealName(userDTO.getRealName());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setRoleId(userDTO.getRoleId());
        user.setStatus(userDTO.getStatus() != null ? userDTO.getStatus() : 1);

        userMapper.insert(user);
        return Result.success();
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO userDTO) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(1007, "用户不存在");
        }

        // 检查用户名唯一性
        if (StringUtils.hasText(userDTO.getUsername()) && !userDTO.getUsername().equals(user.getUsername())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, userDTO.getUsername()));
            if (count > 0) {
                return Result.error(2001, "用户名已存在");
            }
        }

        if (userDTO.getRealName() != null) user.setRealName(userDTO.getRealName());
        if (userDTO.getEmail() != null) user.setEmail(userDTO.getEmail());
        if (userDTO.getPhone() != null) user.setPhone(userDTO.getPhone());
        if (userDTO.getRoleId() != null) user.setRoleId(userDTO.getRoleId());
        if (userDTO.getStatus() != null) user.setStatus(userDTO.getStatus());
        if (StringUtils.hasText(userDTO.getPassword())) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }

        userMapper.updateById(user);
        return Result.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 获取所有角色列表
     */
    @GetMapping("/roles")
    public Result<List<Role>> listRoles() {
        List<Role> roles = roleMapper.selectList(new LambdaQueryWrapper<>());
        return Result.success(roles);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        User user = userMapper.selectById(userId);
        // BUG-12修复：user 可能为 null（如账号已被删除），需先做 null 检查
        if (user == null) {
            return Result.error(2001, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error(2002, "原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success();
    }

    private void fillRoleInfo(User user) {
        if (user.getRoleId() != null) {
            Role role = roleMapper.selectById(user.getRoleId());
            if (role != null) {
                user.setRoleName(role.getRoleName());
                user.setRoleKey(role.getRoleKey());
            }
        }
    }
}
