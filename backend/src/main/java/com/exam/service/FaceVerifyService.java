package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.result.Result;
import com.exam.mapper.*;
import com.exam.pojo.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Base64;

/**
 * 人脸核验业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaceVerifyService {

    private final UserFaceMapper userFaceMapper;
    private final FaceVerifyRecordMapper faceVerifyRecordMapper;
    private final ExamTokenMapper examTokenMapper;
    private final UserMapper userMapper;
    private final FaceCompareService faceCompareService;

    @Value("${app.face.verify-expire-minutes:30}")
    private int verifyExpireMinutes;

    /**
     * 上传用户注册照片（纯数据库存储，不再写文件系统）
     */
    public Result<Void> uploadRegistrationPhoto(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择照片");
        }
        // P3: 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("请上传图片文件（支持JPEG、PNG等格式）");
        }
        // P3: 校验文件大小（5MB上限）
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error("照片大小不能超过5MB");
        }

        try {
            byte[] photoData = file.getBytes();

            // 更新或创建人脸信息
            UserFace existing = userFaceMapper.selectOne(
                    new LambdaQueryWrapper<UserFace>().eq(UserFace::getUserId, userId));

            if (existing != null) {
                // 保留旧的photoUrl作为兼容引用，但不再写入新文件
                existing.setPhotoData(photoData);
                existing.setStatus(1);
                userFaceMapper.updateById(existing);
            } else {
                UserFace userFace = new UserFace();
                userFace.setUserId(userId);
                // photoUrl留空，纯数据库存储模式
                userFace.setPhotoData(photoData);
                userFace.setStatus(1);
                userFaceMapper.insert(userFace);
            }

            return Result.success();
        } catch (Exception e) {
            log.error("上传注册照片失败: {}", e.getMessage());
            return Result.error("照片上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户注册照片信息（返回Base64编码的照片数据，供前端face-api.js比对用）
     */
    public Result<Map<String, Object>> getRegistrationPhoto(Long userId) {
        UserFace userFace = userFaceMapper.selectOne(
                new LambdaQueryWrapper<UserFace>().eq(UserFace::getUserId, userId));

        Map<String, Object> result = new HashMap<>();
        if (userFace != null && userFace.getStatus() == 1) {
            result.put("hasPhoto", true);
            result.put("photoUrl", userFace.getPhotoUrl());
            // 优先返回数据库中的照片数据(Base64)
            if (userFace.getPhotoData() != null && userFace.getPhotoData().length > 0) {
                String base64 = Base64.getEncoder().encodeToString(userFace.getPhotoData());
                result.put("photoData", "data:image/jpeg;base64," + base64);
                result.put("photoSource", "database");
            } else {
                result.put("photoData", null);
                result.put("photoSource", "file");
            }
        } else {
            result.put("hasPhoto", false);
        }
        return Result.success(result);
    }

    /**
     * 考前人脸核验
     */
    public Result<Map<String, Object>> verifyFace(Long userId, Long examId, MultipartFile file) {
        Map<String, Object> result = new HashMap<>();

        // 1. 检查用户是否已上传注册照片
        UserFace userFace = userFaceMapper.selectOne(
                new LambdaQueryWrapper<UserFace>()
                        .eq(UserFace::getUserId, userId)
                        .eq(UserFace::getStatus, 1));

        if (userFace == null) {
            return Result.error("请先上传注册照片");
        }

        // 2. 保存核验照片到数据库（不再写文件系统）
        byte[] verifyPhotoData = null;
        try {
            verifyPhotoData = file.getBytes();
        } catch (Exception e) {
            return Result.error("核验照片读取失败");
        }

        // 3. 执行人脸比对
        double similarity = 0.0;
        boolean passed = false;
        String failReason = null;

        if (faceCompareService.isFaceEnabled()) {
            // 使用数据库中存储的注册照数据进行比对
            // 如果photoData为空且有旧photoUrl，则回退到文件路径
            if (userFace.getPhotoData() != null && userFace.getPhotoData().length > 0) {
                similarity = faceCompareService.compareBytes(userFace.getPhotoData(), verifyPhotoData);
            } else if (userFace.getPhotoUrl() != null) {
                similarity = faceCompareService.compare(userFace.getPhotoUrl(), verifyPhotoData);
            } else {
                // P0: 无注册照时拒绝核验，不允许绕过
                passed = false;
                failReason = "未找到注册照片数据，无法进行人脸比对";
            }
            if (!passed) {
                passed = similarity >= faceCompareService.getFaceThreshold();
                if (!passed) {
                    failReason = "人脸相似度不足（" + String.format("%.2f", similarity * 100) + "%），需达到" 
                            + String.format("%.0f", faceCompareService.getFaceThreshold() * 100) + "%";
                }
            }
        } else {
            // 未启用人脸比对，直接通过
            passed = true;
            similarity = 1.0;
        }

        // 4. 记录核验结果
        FaceVerifyRecord record = new FaceVerifyRecord();
        record.setUserId(userId);
        record.setExamId(examId);
        record.setVerifyPhotoData(verifyPhotoData);
        record.setSimilarity(similarity);
        record.setVerifyResult(passed ? 1 : 0);
        record.setFailReason(failReason);
        record.setVerifyTime(LocalDateTime.now());
        faceVerifyRecordMapper.insert(record);

        result.put("passed", passed);
        result.put("similarity", similarity);
        result.put("failReason", failReason);
        result.put("recordId", record.getId());

        // 5. 核验通过，颁发考试凭证
        if (passed) {
            String token = generateExamToken(userId, examId);
            result.put("examToken", token);
            result.put("tokenExpireTime", LocalDateTime.now().plusMinutes(verifyExpireMinutes));
        }

        return Result.success(result);
    }

    /**
     * 验证考试凭证是否有效
     */
    public boolean validateExamToken(Long userId, Long examId, String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        ExamToken examToken = examTokenMapper.selectOne(
                new LambdaQueryWrapper<ExamToken>()
                        .eq(ExamToken::getToken, token)
                        .eq(ExamToken::getUserId, userId)
                        .eq(ExamToken::getExamId, examId)
                        .eq(ExamToken::getUsed, 0));

        if (examToken == null) {
            return false;
        }

        // 检查是否过期
        if (examToken.getExpireTime().isBefore(LocalDateTime.now())) {
            return false;
        }

        return true;
    }

    /**
     * 使用考试凭证（开始考试时调用）
     */
    public void consumeExamToken(Long userId, Long examId, String token) {
        ExamToken examToken = examTokenMapper.selectOne(
                new LambdaQueryWrapper<ExamToken>()
                        .eq(ExamToken::getToken, token)
                        .eq(ExamToken::getUserId, userId)
                        .eq(ExamToken::getExamId, examId));

        if (examToken != null) {
            examToken.setUsed(1);
            examTokenMapper.updateById(examToken);
        }
    }

    /**
     * 生成考试凭证Token
     */
    private String generateExamToken(Long userId, Long examId) {
        String token = UUID.randomUUID().toString().replace("-", "") +
                Long.toHexString(userId) + Long.toHexString(examId);

        ExamToken examToken = new ExamToken();
        examToken.setUserId(userId);
        examToken.setExamId(examId);
        examToken.setToken(token);
        examToken.setExpireTime(LocalDateTime.now().plusMinutes(verifyExpireMinutes));
        examToken.setUsed(0);
        examTokenMapper.insert(examToken);

        return token;
    }
}
