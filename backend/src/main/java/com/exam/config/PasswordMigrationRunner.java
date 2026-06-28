package com.exam.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.mapper.UserMapper;
import com.exam.pojo.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时自动检测并迁移明文密码为BCrypt加密
 * 解决旧数据库中密码为明文存储导致登录/修改密码失败的问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            log.info("===== 密码迁移检查开始 =====");

            // 查询所有非逻辑删除的用户
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getStatus, 1));

            int migratedCount = 0;
            for (User user : users) {
                String password = user.getPassword();
                if (password == null || password.isBlank()) {
                    log.warn("用户 {} 密码为空，跳过", user.getUsername());
                    continue;
                }

                // BCrypt密码以 $2a$ 或 $2b$ 开头，长度为60
                if (password.startsWith("$2a$") || password.startsWith("$2b$")) {
                    continue; // 已经是BCrypt加密，跳过
                }

                // 检测到明文密码，迁移为BCrypt
                log.info("检测到明文密码，迁移用户: {} (id={})", user.getUsername(), user.getId());
                String encoded = passwordEncoder.encode(password);
                User update = new User();
                update.setId(user.getId());
                update.setPassword(encoded);
                userMapper.updateById(update);
                migratedCount++;
            }

            if (migratedCount > 0) {
                log.info("===== 密码迁移完成，共迁移 {} 个用户的密码 =====", migratedCount);
            } else {
                log.info("===== 密码迁移检查完成，无需迁移 =====");
            }
        } catch (Exception e) {
            log.error("密码迁移失败: {}", e.getMessage(), e);
        }
    }
}
