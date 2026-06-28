package com.exam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.result.Result;
import com.exam.pojo.ViolationRecord;
import com.exam.service.ViolationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 违规记录管理控制器（教师）
 */
@RestController
@RequestMapping("/teacher/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;

    /**
     * 分页查询违规记录（教师监考大屏）
     */
    @GetMapping
    public Result<Page<ViolationRecord>> listViolations(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String violationType) {

        Page<ViolationRecord> result = violationService.listViolations(pageNum, pageSize, examId, studentId, violationType);
        return Result.success(result);
    }

    /**
     * 获取某场考试的实时违规统计
     */
    @GetMapping("/stats/{examId}")
    public Result<Map<String, Object>> getViolationStats(@PathVariable Long examId) {
        Map<String, Object> stats = violationService.getViolationStats(examId);
        return Result.success(stats);
    }
}
