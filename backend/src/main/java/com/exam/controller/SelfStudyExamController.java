package com.exam.controller;

import com.exam.common.result.Result;
import com.exam.dto.CreateSelfStudyExamDTO;
import com.exam.dto.JoinExamByCodeDTO;
import com.exam.pojo.SelfStudyExam;
import com.exam.service.SelfStudyExamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI自助考试控制器（学生端）
 */
@Slf4j
@RestController
@RequestMapping("/student/self-study")
@RequiredArgsConstructor
public class SelfStudyExamController {

    private final SelfStudyExamService selfStudyExamService;

    /**
     * 创建AI自助考试（先创建记录，然后异步触发生成）
     */
    @PostMapping("/create")
    public Result<SelfStudyExam> create(@Valid @RequestBody CreateSelfStudyExamDTO dto,
                                         HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        SelfStudyExam exam = selfStudyExamService.createSelfStudyExam(
                studentId, dto.getPrompt(), dto.getDuration(), dto.getStageKey());
        return Result.success(exam);
    }

    /**
     * 复习路径各阶段的测验结果（用于把考试分数回填成阶段状态）。
     */
    @GetMapping("/stage-progress")
    public Result<List<Map<String, Object>>> stageProgress(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        return Result.success(selfStudyExamService.getStageProgress(studentId));
    }

    /**
     * 依据某次考试的错题创建专项巩固卷。
     *
     * 只创建记录（status=0），题目生成仍需前端接着调用 /{examId}/generate，
     * 与「AI创建考试」保持同一套流程，避免两处等待逻辑不一致。
     */
    @PostMapping("/from-wrong-questions")
    public Result<SelfStudyExam> createFromWrongQuestions(@RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Object recordId = body.get("examRecordId");
        if (recordId == null) {
            return Result.error(400, "缺少考试记录ID");
        }

        Long examRecordId = Long.valueOf(recordId.toString());
        Integer duration = body.get("duration") != null
                ? Integer.valueOf(body.get("duration").toString()) : null;

        return Result.success(
                selfStudyExamService.createFromWrongQuestions(studentId, examRecordId, duration));
    }

    /**
     * 触发AI生成题目（创建后调用，同步生成）
     */
    @PostMapping("/{examId}/generate")
    public Result<Map<String, Object>> generate(@PathVariable Long examId,
                                                 HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        SelfStudyExamService.GenerateOutcome outcome =
                selfStudyExamService.generateAndUpdateExam(examId, studentId);
        // 同时返回考试对象与出卷质量报告，前端据此提示被剔除/存疑的题目
        return Result.success(toPayload(outcome));
    }

    /**
     * 获取生成状态（前端轮询用）
     */
    @GetMapping("/{examId}/status")
    public Result<Map<String, Object>> getGenerateStatus(@PathVariable Long examId,
                                                          HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> status = selfStudyExamService.getGenerateStatus(examId, studentId);
        return Result.success(status);
    }

    /**
     * 获取我参与的自助考试列表
     */
    @GetMapping("/list")
    public Result<List<SelfStudyExam>> myList(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<SelfStudyExam> exams = selfStudyExamService.getMySelfStudyExams(studentId);
        return Result.success(exams);
    }

    /**
     * 获取自助考试详情
     */
    @GetMapping("/{examId}")
    public Result<Map<String, Object>> detail(@PathVariable Long examId,
                                               HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> detail = selfStudyExamService.getExamDetail(examId, studentId);
        return Result.success(detail);
    }

    /**
     * 获取考试码（创建者查看）
     */
    @GetMapping("/{examId}/exam-code")
    public Result<Map<String, Object>> getExamCode(@PathVariable Long examId,
                                                    HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        String code = selfStudyExamService.getExamCode(examId, studentId);
        return Result.success(Map.of("examCode", code));
    }

    /**
     * 发布考试（创建者操作）
     */
    @PostMapping("/{examId}/publish")
    public Result<Void> publish(@PathVariable Long examId,
                                HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        selfStudyExamService.publishExam(examId, studentId);
        return Result.success();
    }

    /**
     * 通过考试码预览考试信息
     */
    @GetMapping("/preview")
    public Result<Map<String, Object>> previewByCode(@RequestParam String examCode) {
        Map<String, Object> info = selfStudyExamService.previewByExamCode(examCode);
        return Result.success(info);
    }

    /**
     * 通过考试码加入考试
     */
    @PostMapping("/join")
    public Result<SelfStudyExam> join(@Valid @RequestBody JoinExamByCodeDTO dto,
                                       HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        SelfStudyExam exam = selfStudyExamService.joinByExamCode(dto.getExamCode(), studentId);
        return Result.success(exam);
    }

    /**
     * 自助重考
     */
    @PostMapping("/{examId}/retake")
    public Result<Void> retake(@PathVariable Long examId,
                               HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        selfStudyExamService.selfRetake(examId, studentId);
        return Result.success();
    }

    /**
     * 获取历史成绩列表
     */
    @GetMapping("/{examId}/scores")
    public Result<List<Map<String, Object>>> getScores(@PathVariable Long examId,
                                                        HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<Map<String, Object>> scores = selfStudyExamService.getExamScores(examId, studentId);
        return Result.success(scores);
    }

    /**
     * 查看试卷内容（不含答案）
     */
    @GetMapping("/{examId}/paper")
    public Result<Map<String, Object>> getPaper(@PathVariable Long examId,
                                                 HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> paper = selfStudyExamService.getExamPaper(examId, studentId);
        return Result.success(paper);
    }

    /**
     * 查看某次答卷详情（含学生答案、正确答案、解析）
     * @param recordId 考试记录ID
     */
    @GetMapping("/{examId}/paper/{recordId}")
    public Result<Map<String, Object>> getPaperWithAnswers(@PathVariable Long examId,
                                                            @PathVariable Long recordId,
                                                            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> paper = selfStudyExamService.getExamPaperWithAnswers(examId, studentId, recordId);
        return Result.success(paper);
    }

    /**
     * 重新生成试卷（修改prompt/duration后清除旧数据重新调用AI生成）
     * 仅创建者可操作
     */
    @PostMapping("/{examId}/regenerate")
    public Result<Map<String, Object>> regenerate(@PathVariable Long examId,
                                                   @RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        String newPrompt = body.get("prompt") != null ? body.get("prompt").toString() : null;
        Integer newDuration = body.get("duration") != null
                ? Integer.parseInt(body.get("duration").toString()) : null;
        SelfStudyExamService.GenerateOutcome outcome =
                selfStudyExamService.regenerateExam(examId, studentId, newPrompt, newDuration);
        return Result.success(toPayload(outcome));
    }

    /** 统一组装「考试 + 质量报告」的响应体 */
    private Map<String, Object> toPayload(SelfStudyExamService.GenerateOutcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exam", outcome.exam());
        payload.put("quality", outcome.quality());
        return payload;
    }
}
