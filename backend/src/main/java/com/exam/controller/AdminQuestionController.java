package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.service.KnowledgeTopicCatalog;
import com.exam.service.QuestionTopicBackfillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 题库维护接口（仅管理员，路径受 SecurityConfig 的 /admin/** 规则保护）。
 */
@RestController
@RequestMapping("/admin/questions")
@RequiredArgsConstructor
public class AdminQuestionController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final QuestionTopicBackfillService backfillService;
    private final KnowledgeTopicCatalog catalog;

    /**
     * 给存量题目补知识点，一次一小批。
     *
     * <p>每道题都要调一次模型，所以故意限制单批上限 50：跑一批看一眼结果，
     * 再决定是否继续，比一次跑 2000 道然后不知道卡在哪要可靠。
     *
     * @param limit  本批处理数量（1~50，超出自动收敛）
     * @param dryRun 只判定不落库，先用来看效果
     */
    @PostMapping("/backfill-topics")
    public Result<Map<String, Object>> backfillTopics(
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return Result.success(backfillService.backfill(safeLimit, dryRun));
    }

    /**
     * 查看知识点词表（排查「为什么这题标不到」时用）。
     */
    @GetMapping("/topic-catalog")
    public Result<List<String>> topicCatalog() {
        return Result.success(catalog.getTopics());
    }
}
