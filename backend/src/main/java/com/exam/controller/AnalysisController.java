package com.exam.controller;

import com.exam.common.exception.BusinessException;
import com.exam.common.result.Result;
import com.exam.mapper.AnswerRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Question;
import com.exam.service.GradingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 主观题批改控制器（教师）
 */
@Slf4j
@RestController
@RequestMapping("/teacher/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final GradingService gradingService;
    private final AnswerRecordMapper answerRecordMapper;
    private final QuestionMapper questionMapper;

    /**
     * 获取待批改的主观题列表
     */
    @GetMapping("/ungraded/{examId}")
    public Result<List<AnswerRecord>> getUngradedAnswers(@PathVariable Long examId) {
        try {
            List<AnswerRecord> answers = gradingService.getUngradedAnswers(examId);
            // 填充题目信息
            if (!answers.isEmpty()) {
                List<Long> questionIds = answers.stream()
                    .map(AnswerRecord::getQuestionId).distinct().toList();
                Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                    .collect(java.util.stream.Collectors.toMap(Question::getId, q -> q));
                
                answers.forEach(a -> {
                    Question q = questionMap.get(a.getQuestionId());
                    if (q != null) {
                        a.setQuestionContent(q.getContent());
                        a.setQuestionType(q.getType());
                        // 用「试卷里给这道题的分值」，不是题目自身分值。
                        // 题目自身 5 分、试卷给 20 分时，这里回题目分会让批改界面
                        // 显示错误的上限，老师想给 15 分都过不去。
                        int paperScore = gradingService.fullScoreOf(examId, a.getQuestionId());
                        a.setQuestionScore(paperScore > 0 ? paperScore
                                : (q.getScore() != null ? q.getScore() : 0));
                    }
                });
            }
            return Result.success(answers);
        } catch (Exception e) {
            log.error("获取待批改列表失败, examId={}", examId, e);
            throw new BusinessException("获取待批改列表失败，请稍后重试");
        }
    }

    /**
     * 批改主观题
     */
    @PostMapping("/grade")
    public Result<Void> gradeAnswer(@RequestBody Map<String, Object> params) {
        if (params.get("answerId") == null) {
            return Result.error(400, "缺少答题记录ID");
        }
        if (params.get("score") == null) {
            return Result.error(400, "缺少评分");
        }

        try {
            Long answerId = Long.valueOf(params.get("answerId").toString());
            Integer score = Integer.valueOf(params.get("score").toString());
            String comment = params.get("comment") != null ? params.get("comment").toString() : null;
            gradingService.gradeAnswer(answerId, score, comment);
            return Result.success();
        } catch (NumberFormatException e) {
            log.warn("批改参数格式错误: {}", params);
            return Result.error(400, "参数格式错误");
        }
    }
}
