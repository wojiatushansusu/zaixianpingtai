package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.mapper.AnswerRecordMapper;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.mapper.QuestionMapper;
import com.exam.pojo.AnswerRecord;
import com.exam.pojo.Exam;
import com.exam.pojo.ExamRecord;
import com.exam.pojo.Paper;
import com.exam.pojo.Question;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主观题批改服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingService {

    private final AnswerRecordMapper answerRecordMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamMapper examMapper;
    private final QuestionMapper questionMapper;

    /**
     * 获取需要批改的主观题列表
     * 
     * 查询逻辑：先从答案记录中找出该考试涉及的所有题目，
     * 再过滤出主观题(type=4)中未批改的记录。
     * 这样确保只查询该考试实际作答的主观题，而非全库主观题。
     */
    public List<AnswerRecord> getUngradedAnswers(Long examId) {
        // 获取已提交的考试记录
        List<ExamRecord> records = examRecordMapper.selectList(
            new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId)
                .ne(ExamRecord::getStatus, 0));

        if (records.isEmpty()) return List.of();

        List<Long> recordIds = records.stream().map(ExamRecord::getId).toList();

        // 获取该考试的所有答案记录
        List<AnswerRecord> allAnswers = answerRecordMapper.selectList(
            new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getRecordId, recordIds));

        // 找出所有涉及的主观题ID
        List<Long> allQuestionIds = allAnswers.stream()
            .map(AnswerRecord::getQuestionId).distinct().toList();

        if (allQuestionIds.isEmpty()) return List.of();

        // 批量查询题目类型，筛选出主观题
        List<Question> questions = questionMapper.selectBatchIds(allQuestionIds);
        List<Long> subjectiveQuestionIds = questions.stream()
            .filter(q -> q.getType() != null && q.getType() == 4)
            .map(Question::getId).toList();

        if (subjectiveQuestionIds.isEmpty()) return List.of();

        // 获取未批改的主观题答案
        return answerRecordMapper.selectList(
            new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getRecordId, recordIds)
                .in(AnswerRecord::getQuestionId, subjectiveQuestionIds)
                .isNull(AnswerRecord::getTeacherScore));
    }

    /**
     * 教师批改主观题
     */
    @Transactional
    public void gradeAnswer(Long answerId, Integer score, String comment) {
        AnswerRecord answer = answerRecordMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("答题记录不存在");
        }

        // 评分合法性校验
        // BUG-14修复：同时校验评分不能超过该题满分值
        if (score != null && score < 0) {
            throw new BusinessException("评分不能为负数");
        }
        if (score != null) {
            // 获取题目满分值进行上限校验
            AnswerRecord tmpAnswer = answer; // 已在上方查询
            // 通过 answer 找到关联题目，获取该题在试卷中的分值
            List<AnswerRecord> sameQuestion = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                    .eq(AnswerRecord::getRecordId, tmpAnswer.getRecordId())
                    .eq(AnswerRecord::getQuestionId, tmpAnswer.getQuestionId()));
            // 找试卷满分：用该题的 score 字段（autoGrade 时已写入 pq.score）
            int maxScore = sameQuestion.stream()
                .filter(a -> a.getScore() != null)
                .mapToInt(AnswerRecord::getScore)
                .max().orElse(Integer.MAX_VALUE);
            // 如果 autoGrade 对主观题 score=0，直接查题目的 score 字段
            if (maxScore == 0) {
                Question q = questionMapper.selectById(tmpAnswer.getQuestionId());
                if (q != null && q.getScore() != null) maxScore = q.getScore();
            }
            if (maxScore > 0 && score > maxScore) {
                throw new BusinessException("评分不能超过题目满分值 " + maxScore);
            }
        }

        answer.setTeacherScore(score);
        answer.setTeacherComment(comment);
        answerRecordMapper.updateById(answer);

        // 重新计算考试总分
        recalculateExamScore(answer.getRecordId());
    }

    /**
     * 批量批改主观题
     */
    @Transactional
    public void batchGrade(List<Long> answerIds, List<Integer> scores, List<String> comments) {
        // 一次性查询所有需要批改的答案记录
        List<AnswerRecord> answerRecords = answerRecordMapper.selectBatchIds(answerIds);
        Map<Long, AnswerRecord> answerMap = answerRecords.stream()
            .collect(Collectors.toMap(AnswerRecord::getId, a -> a));

        for (int i = 0; i < answerIds.size(); i++) {
            AnswerRecord answer = answerMap.get(answerIds.get(i));
            if (answer != null && scores.get(i) != null) {
                answer.setTeacherScore(scores.get(i));
                if (comments != null && i < comments.size() && comments.get(i) != null) {
                    answer.setTeacherComment(comments.get(i));
                }
                answerRecordMapper.updateById(answer);
            }
        }

        // 重新计算所有相关考试的总分
        for (AnswerRecord answer : answerRecords) {
            recalculateExamScore(answer.getRecordId());
        }
    }

    /**
     * 重新计算考试总分
     */
    private void recalculateExamScore(Long recordId) {
        List<AnswerRecord> answers = answerRecordMapper.selectList(
            new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, recordId));

        int totalScore = 0;
        for (AnswerRecord answer : answers) {
            // 主观题使用教师评分
            if (answer.getTeacherScore() != null) {
                totalScore += answer.getTeacherScore();
            } else {
                totalScore += answer.getScore() != null ? answer.getScore() : 0;
            }
        }

        // 更新考试记录
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record != null) {
            record.setScore(totalScore);
            // 从试卷获取及格分，而非硬编码60
            int passScore = getPassScoreByExamId(record.getExamId());
            record.setIsPassed(totalScore >= passScore ? 1 : 0);
            examRecordMapper.updateById(record);
        }
    }

    /**
     * 根据考试ID获取关联试卷的及格分
     */
    private int getPassScoreByExamId(Long examId) {
        try {
            Exam exam = examMapper.selectById(examId);
            if (exam != null && exam.getPaperId() != null) {
                // 通过 ExamService 中已有的逻辑获取及格分
                // 直接从 exam 对象获取（Exam 实体已有 passScore 字段）
                if (exam.getPassScore() != null) {
                    return exam.getPassScore();
                }
            }
        } catch (Exception e) {
            log.warn("获取及格分失败，使用默认值60: {}", e.getMessage());
        }
        return 60;
    }
}
