package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.mapper.UserMapper;
import com.exam.pojo.ExamOnlineStudent;
import com.exam.pojo.User;
import com.exam.pojo.VideoEvidence;
import com.exam.service.ExamService;
import com.exam.service.OnlineStatusService;
import com.exam.service.VideoEvidenceService;
import com.exam.websocket.ExamMonitorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 考试监控控制器（教师）
 */
@RestController
@RequestMapping("/teacher/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final OnlineStatusService onlineStatusService;
    private final VideoEvidenceService videoEvidenceService;
    private final UserMapper userMapper;
    private final ExamMonitorWebSocketHandler wsHandler;
    private final ExamService examService;

    /**
     * 获取考试在线学生统计
     */
    @GetMapping("/online-stats/{examId}")
    public Result<Map<String, Object>> getOnlineStats(@PathVariable Long examId) {
        return Result.success(onlineStatusService.getOnlineStats(examId));
    }

    /**
     * 获取在线学生列表
     */
    @GetMapping("/online-students/{examId}")
    public Result<List<ExamOnlineStudent>> getOnlineStudents(@PathVariable Long examId) {
        List<ExamOnlineStudent> students = onlineStatusService.getOnlineStudents(examId);
        // 填充学生姓名
        students.forEach(s -> {
            User student = userMapper.selectById(s.getStudentId());
            if (student != null) {
                s.setStudentName(student.getRealName());
                s.setStudentUsername(student.getUsername());
            }
        });
        return Result.success(students);
    }

    /**
     * 获取视频证据列表
     */
    @GetMapping("/evidence/{examId}")
    public Result<List<VideoEvidence>> getVideoEvidence(
            @PathVariable Long examId,
            @RequestParam(required = false) Long studentId) {
        List<VideoEvidence> evidence = videoEvidenceService.getExamEvidence(examId, studentId);
        evidence.forEach(e -> {
            User student = userMapper.selectById(e.getStudentId());
            if (student != null) e.setStudentName(student.getRealName());
        });
        return Result.success(evidence);
    }

    /**
     * 发送警告给学生
     */
    @PostMapping("/warning")
    public Result<Void> sendWarning(@RequestBody Map<String, Object> params) {
        Long targetUserId = getLongParam(params, "targetUserId");
        String message = (String) params.get("message");
        Long examId = getLongParam(params, "examId");

        if (targetUserId == null) {
            return Result.error(400, "缺少目标学生ID");
        }
        if (message == null || message.isBlank()) {
            message = "请注意考试纪律";
        }

        wsHandler.sendToUser(targetUserId, "warning", Map.of(
                "message", message,
                "fromExamId", examId != null ? examId : 0
        ));
        return Result.success();
    }

    /**
     * 强制交卷
     */
    @PostMapping("/force-submit")
    public Result<Void> forceSubmit(@RequestBody Map<String, Object> params) {
        Long targetUserId = getLongParam(params, "targetUserId");
        Long examId = getLongParam(params, "examId");
        String reason = (String) params.getOrDefault("reason", "监考教师强制交卷");

        if (targetUserId == null || examId == null) {
            return Result.error(400, "缺少必要参数");
        }

        // 1. 通过WebSocket通知学生端
        wsHandler.sendToUser(targetUserId, "force_submit", Map.of("reason", reason));

        // 2. 后端自动提交该学生的考试
        try {
            examService.submitExam(examId, targetUserId, null);
        } catch (Exception e) {
            // 学生可能已经提交，记录日志即可
            return Result.error("该学生可能已提交考试");
        }

        return Result.success();
    }

    private Long getLongParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
