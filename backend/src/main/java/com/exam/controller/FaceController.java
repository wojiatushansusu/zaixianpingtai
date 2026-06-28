package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.pojo.Exam;
import com.exam.service.FaceVerifyService;
import com.exam.mapper.ExamMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 人脸核验控制器
 */
@RestController
@RequiredArgsConstructor
public class FaceController {

    private final FaceVerifyService faceVerifyService;
    private final ExamMapper examMapper;

    /**
     * 上传注册照片（通用接口，所有已登录用户可用）
     */
    @PostMapping({"/student/face/register", "/user/face/register"})
    public Result<Void> uploadRegistrationPhoto(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return faceVerifyService.uploadRegistrationPhoto(userId, file);
    }

    /**
     * 获取注册照片状态（通用接口）
     */
    @GetMapping({"/student/face/register", "/user/face/register"})
    public Result<Map<String, Object>> getRegistrationPhoto(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return faceVerifyService.getRegistrationPhoto(userId);
    }

    /**
     * 考前人脸核验（学生）
     */
    @PostMapping("/student/exams/{examId}/face-verify")
    public Result<Map<String, Object>> verifyFace(
            @PathVariable Long examId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        // 检查考试是否需要人脸核验
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return Result.error("考试不存在");
        }
        if (exam.getRequireFaceVerify() != null && exam.getRequireFaceVerify() == 0) {
            // 不需要核验，直接通过
            Map<String, Object> result = Map.of("passed", true, "similarity", 1.0, "skipVerify", true);
            return Result.success(result);
        }

        return faceVerifyService.verifyFace(userId, examId, file);
    }

    /**
     * 验证考试凭证（学生 - 开始考试时）
     */
    @GetMapping("/student/exams/{examId}/verify-token")
    public Result<Map<String, Object>> verifyToken(
            @PathVariable Long examId,
            @RequestParam(required = false) String examToken,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return Result.error("考试不存在");
        }

        boolean needVerify = exam.getRequireFaceVerify() != null && exam.getRequireFaceVerify() == 1;

        if (!needVerify) {
            return Result.success(Map.of("needVerify", false, "tokenValid", true));
        }

        boolean valid = faceVerifyService.validateExamToken(userId, examId, examToken);
        // 注意：此处仅验证Token有效性，不消耗Token
        // Token在startExam中开始考试时才真正消耗

        return Result.success(Map.of("needVerify", true, "tokenValid", valid));
    }
}
