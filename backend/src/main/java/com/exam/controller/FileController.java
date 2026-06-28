package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.mapper.UserMapper;
import com.exam.pojo.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 文件上传控制器（纯数据库存储模式）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final UserMapper userMapper;

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    /**
     * 上传头像（直接存user.avatar_data字段）
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.error(7001, "文件不能为空");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            return Result.error(7002, "仅支持 JPG/PNG/GIF/WEBP 格式");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(7003, "文件大小不能超过5MB");
        }

        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId == null) {
                return Result.error(7004, "未登录或身份过期");
            }

            byte[] avatarData = file.getBytes();
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setAvatarData(avatarData);
                userMapper.updateById(user);
            }

            // 返回Base64 data URI（前端可直接使用）
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String dataUri = "data:" + contentType + ";base64," +
                    Base64.getEncoder().encodeToString(avatarData);
            return Result.success(dataUri);
        } catch (IOException e) {
            log.error("头像上传失败", e);
            return Result.error(7001, "头像上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前用户头像（从数据库读取，返回Base64）
     */
    @GetMapping("/avatar/me")
    public Result<String> getMyAvatar(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(7004, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user != null && user.getAvatarData() != null && user.getAvatarData().length > 0) {
            // 从avatar字段推断类型，默认jpeg
            String ext = user.getAvatar() != null && user.getAvatar().contains(".") ?
                    user.getAvatar().substring(user.getAvatar().lastIndexOf(".")) : ".jpg";
            String mime = switch (ext.toLowerCase()) {
                case ".png" -> "image/png";
                case ".gif" -> "image/gif";
                case ".webp" -> "image/webp";
                default -> "image/jpeg";
            };
            String dataUri = "data:" + mime + ";base64," +
                    Base64.getEncoder().encodeToString(user.getAvatarData());
            return Result.success(dataUri);
        }
        return Result.success(null);
    }
}
