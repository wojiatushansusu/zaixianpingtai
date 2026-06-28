package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.dto.CreatePracticeDTO;
import com.exam.pojo.PracticeRecord;
import com.exam.service.AiExplainService;
import com.exam.service.WrongQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生端 - 错题本与练习
 */
@RestController
@RequestMapping("/student/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final WrongQuestionService wrongQuestionService;
    private final AiExplainService aiExplainService;

    /**
     * 获取错题列表
     * @param questionType 题目类型筛选（可选）: 1-单选 2-多选 3-判断 4-简答
     */
    @GetMapping
    public Result<List<Map<String, Object>>> getWrongQuestions(
            @RequestParam(required = false) Integer questionType,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> questions = wrongQuestionService.getWrongQuestions(studentId, questionType);
        return Result.success(questions);
    }

    /**
     * 获取错题统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getWrongQuestionStats(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> stats = wrongQuestionService.getWrongQuestionStats(studentId);
        return Result.success(stats);
    }

    /**
     * 获取错题按考试分组列表（一级：试卷，二级：错题）
     */
    @GetMapping("/grouped")
    public Result<List<Map<String, Object>>> getWrongQuestionsGrouped(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> groups = wrongQuestionService.getWrongQuestionsGrouped(studentId);
        return Result.success(groups);
    }

    /**
     * 获取某次考试中的错题详情
     * @param examRecordId 考试记录ID
     */
    @GetMapping("/exam-record/{examRecordId}")
    public Result<List<Map<String, Object>>> getWrongQuestionsByExamRecord(
            @PathVariable Long examRecordId,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> questions = wrongQuestionService.getWrongQuestionsByExamRecord(examRecordId, studentId);
        return Result.success(questions);
    }

    /**
     * 创建错题练习
     */
    @PostMapping("/practice")
    public Result<PracticeRecord> createPractice(@RequestBody CreatePracticeDTO dto,
                                                  HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        PracticeRecord practice = wrongQuestionService.createPractice(studentId, dto);
        return Result.success(practice);
    }

    /**
     * 检查练习模块是否就绪（表是否存在）
     */
    @GetMapping("/practice/check")
    public Result<Map<String, Object>> checkPracticeReady() {
        Map<String, Object> result = wrongQuestionService.checkPracticeReady();
        return Result.success(result);
    }

    /**
     * 获取练习详情
     */
    @GetMapping("/practice/{practiceId}")
    public Result<Map<String, Object>> getPracticeDetail(@PathVariable Long practiceId,
                                                          HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> detail = wrongQuestionService.getPracticeDetail(practiceId, studentId);
        return Result.success(detail);
    }

    /**
     * 保存练习答案
     */
    @PostMapping("/practice/{practiceId}/save-answer")
    public Result<Void> savePracticeAnswer(@PathVariable Long practiceId,
                                            @RequestBody Map<String, String> params,
                                            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Long questionId = Long.parseLong(params.get("questionId"));
        String answer = params.get("answer");
        wrongQuestionService.savePracticeAnswer(practiceId, studentId, questionId, answer);
        return Result.success();
    }

    /**
     * 提交练习
     */
    @PostMapping("/practice/{practiceId}/submit")
    public Result<PracticeRecord> submitPractice(@PathVariable Long practiceId,
                                                   @RequestBody Map<Long, String> answers,
                                                   HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        PracticeRecord result = wrongQuestionService.submitPractice(practiceId, studentId, answers);
        return Result.success(result);
    }

    /**
     * 获取练习结果详情
     */
    @GetMapping("/practice/{practiceId}/result")
    public Result<Map<String, Object>> getPracticeResult(@PathVariable Long practiceId,
                                                          HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> result = wrongQuestionService.getPracticeResult(practiceId, studentId);
        return Result.success(result);
    }

    /**
     * 获取练习历史记录
     */
    @GetMapping("/practice/history")
    public Result<List<PracticeRecord>> getPracticeHistory(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<PracticeRecord> history = wrongQuestionService.getPracticeHistory(studentId);
        return Result.success(history);
    }

    /**
     * AI深度讲解
     * @param body 包含题目信息：content, options(可选), correctAnswer, studentAnswer, analysis(可选), questionTypeDesc(可选)
     */
    @PostMapping("/ai-explain")
    public Result<Map<String, Object>> aiExplain(@RequestBody Map<String, Object> body) {
        if (!aiExplainService.isEnabled()) {
            return Result.error(503, "AI讲解功能未启用，请联系管理员配置API Key");
        }

        String explanation = aiExplainService.generateExplanation(body);
        if (explanation == null) {
            return Result.error(500, "AI讲解生成失败，请稍后重试");
        }

        return Result.success(Map.of(
                "explanation", explanation,
                "provider", aiExplainService.getProviderName()
        ));
    }
}
