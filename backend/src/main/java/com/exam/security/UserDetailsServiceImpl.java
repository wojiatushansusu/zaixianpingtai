package com.exam.security;

import com.exam.common.enums.ResultCodeEnum;
import com.exam.common.exception.BusinessException;
import com.exam.mapper.UserMapper;
import com.exam.pojo.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 用户认证服务 - 实现 Spring Security UserDetailsService
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUsernameWithRole(username);

        // 用户不存在与账号被禁用**都抛 UsernameNotFoundException**，不区分。
        // 原因有两个：
        // 1. 抛 BusinessException 时 Spring 会包成 InternalAuthenticationServiceException，
        //    全局处理器没接住，最终返回 500「系统繁忙」——正常登录失败被记成系统故障
        // 2. 区分「用户不存在」和「密码错误」等于送了一个账号枚举接口：
        //    攻击者能靠返回差异批量确认哪些用户名存在
        // UsernameNotFoundException 会被 Spring 转成 BadCredentialsException，
        // 统一走 401 +「用户名或密码错误」。
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("认证失败");
        }
        return new LoginUser(user);
    }
}
