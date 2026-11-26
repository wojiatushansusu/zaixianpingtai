package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.service.ScoreRecalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 考试数据维护（仅管理员，路径受 SecurityConfig 的 /admin/** 规则保护）。
 */
@RestController
@RequestMapping("/admin/exams")
@RequiredArgsConstructor
public class AdminExamController {

    private final ScoreRecalculationService recalculationService;

    /**
     * 按当前判分规则重算历史答卷。
     *
     * <p>存在的理由：判分规则改了以后，之前交的卷子分数不会自己变。
     * 判断题那条规则修复前，所有判断题都判成 0 分——这些历史记录需要一次性纠正。
     *
     * <p>默认 `dryRun=true`：先把「哪几份、从几分变几分」给出来，
     * 确认无误再传 `dryRun=false` 落库。重算会改写成绩，不提供默认直接执行。
     */
    @PostMapping("/recalculate-scores")
    public Result<Map<String, Object>> recalculateScores(
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return Result.success(recalculationService.recalculate(dryRun));
    }
}
