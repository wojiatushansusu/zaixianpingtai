package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.exception.BusinessException;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.dto.CreatePracticeDTO;
import com.exam.mapper.*;
import com.exam.pojo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 错题集与练习服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WrongQuestionService {

    private final ExamRecordMapper examRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final QuestionMapper questionMapper;
    private final ExamMapper examMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;

    // ==================== 错题本 ====================

    /**
     * 获取学生的错题列表
     * 从已提交的考试记录中筛选 is_correct=0 的答题记录
     *
     * @param studentId 学生ID
     * @param questionType 题目类型筛选（可选）
     * @return 错题列表（包含题目信息和作答信息）
     */
    public List<Map<String, Object>> getWrongQuestions(Long studentId, Integer questionType) {
        // 1. 获取该学生所有已提交的考试记录ID
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0));

        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> recordIds = records.stream().map(ExamRecord::getId).toList();

        // 2. 获取所有答错的记录
        // BUG-13修复：isCorrect=null 表示主观题未批改，也应纳入错题本（未批则视为未得分）
        // 使用 isCorrect != 1 代替 isCorrect = 0，同时匹配 null 和 0
        LambdaQueryWrapper<AnswerRecord> wrapper = new LambdaQueryWrapper<AnswerRecord>()
                .in(AnswerRecord::getRecordId, recordIds)
                .and(w -> w.eq(AnswerRecord::getIsCorrect, 0).or().isNull(AnswerRecord::getIsCorrect));

        List<AnswerRecord> wrongAnswers = answerRecordMapper.selectList(wrapper);

        if (wrongAnswers.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 获取所有涉及的题目ID
        List<Long> questionIds = wrongAnswers.stream()
                .map(AnswerRecord::getQuestionId)
                .distinct()
                .toList();

        // 批量查询题目
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 4. 构建recordId到ExamRecord的映射
        Map<Long, ExamRecord> recordMap = records.stream()
                .collect(Collectors.toMap(ExamRecord::getId, r -> r));

        // 5. 获取考试信息
        Set<Long> examIds = records.stream().map(ExamRecord::getExamId).collect(Collectors.toSet());
        Map<Long, Exam> examMap = examMapper.selectBatchIds(examIds).stream()
                .collect(Collectors.toMap(Exam::getId, e -> e));

        // 6. 去重：同一道题可能在不同考试中答错，取最近的一次
        // 按questionId分组，取最新的answer_record
        Map<Long, AnswerRecord> latestWrongByQuestion = new LinkedHashMap<>();
        for (AnswerRecord answer : wrongAnswers) {
            AnswerRecord existing = latestWrongByQuestion.get(answer.getQuestionId());
            if (existing == null || answer.getId() > existing.getId()) {
                latestWrongByQuestion.put(answer.getQuestionId(), answer);
            }
        }

        // 7. 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, AnswerRecord> entry : latestWrongByQuestion.entrySet()) {
            AnswerRecord answer = entry.getValue();
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            // 按题目类型筛选
            if (questionType != null && !questionType.equals(question.getType())) {
                continue;
            }

            ExamRecord examRecord = recordMap.get(answer.getRecordId());
            Exam exam = examRecord != null ? examMap.get(examRecord.getExamId()) : null;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("answerRecordId", answer.getId());
            item.put("questionId", question.getId());
            item.put("questionType", question.getType());
            item.put("questionTypeDesc", QuestionTypeEnum.fromCode(question.getType()).getDescription());
            item.put("content", question.getContent());
            item.put("optionA", question.getOptionA());
            item.put("optionB", question.getOptionB());
            item.put("optionC", question.getOptionC());
            item.put("optionD", question.getOptionD());
            item.put("studentAnswer", answer.getStudentAnswer());
            item.put("correctAnswer", question.getAnswer());
            item.put("analysis", question.getAnalysis());
            item.put("score", question.getScore());
            item.put("difficulty", question.getDifficulty());
            item.put("examTitle", exam != null ? exam.getTitle() : "未知考试");
            item.put("submitTime", examRecord != null ? examRecord.getSubmitTime() : null);
            item.put("wrongCount", (int) wrongAnswers.stream()
                    .filter(a -> a.getQuestionId().equals(answer.getQuestionId()))
                    .count());

            result.add(item);
        }

        // 按错题次数降序、最近提交时间降序排列
        // BUG-03修复：submitTime 可能为 null，使用 null-safe 比较
        result.sort((a, b) -> {
            int countCompare = (int) b.get("wrongCount") - (int) a.get("wrongCount");
            if (countCompare != 0) return countCompare;
            LocalDateTime timeA = (LocalDateTime) a.get("submitTime");
            LocalDateTime timeB = (LocalDateTime) b.get("submitTime");
            if (timeB == null && timeA == null) return 0;
            if (timeB == null) return -1;
            if (timeA == null) return 1;
            return timeB.compareTo(timeA);
        });

        return result;
    }

    /**
     * 获取错题统计信息
     */
    public Map<String, Object> getWrongQuestionStats(Long studentId) {
        List<Map<String, Object>> wrongQuestions = getWrongQuestions(studentId, null);
        int totalWrong = wrongQuestions.size();

        // 按题目类型统计
        Map<Integer, Long> typeCount = wrongQuestions.stream()
                .collect(Collectors.groupingBy(q -> (Integer) q.get("questionType"), Collectors.counting()));

        // 按难度统计
        Map<Integer, Long> difficultyCount = wrongQuestions.stream()
                .filter(q -> q.get("difficulty") != null)
                .collect(Collectors.groupingBy(q -> (Integer) q.get("difficulty"), Collectors.counting()));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWrong", totalWrong);
        stats.put("singleChoiceWrong", typeCount.getOrDefault(1, 0L));
        stats.put("multiChoiceWrong", typeCount.getOrDefault(2, 0L));
        stats.put("judgeWrong", typeCount.getOrDefault(3, 0L));
        stats.put("essayWrong", typeCount.getOrDefault(4, 0L));
        stats.put("easyWrong", difficultyCount.getOrDefault(1, 0L));
        stats.put("mediumWrong", difficultyCount.getOrDefault(2, 0L));
        stats.put("hardWrong", difficultyCount.getOrDefault(3, 0L));

        return stats;
    }

    /**
     * 获取错题按考试分组列表
     * 返回每个考试作为一组，包含考试信息和该考试下的错题数量
     */
    public List<Map<String, Object>> getWrongQuestionsGrouped(Long studentId) {
        // 1. 获取该学生所有已提交的考试记录
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getSubmitTime));

        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> recordIds = records.stream().map(ExamRecord::getId).toList();

        // 2. 获取所有答错的记录
        List<AnswerRecord> wrongAnswers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .in(AnswerRecord::getRecordId, recordIds)
                        .eq(AnswerRecord::getIsCorrect, 0));

        if (wrongAnswers.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 按recordId分组统计错题数
        Map<Long, Long> wrongCountByRecord = wrongAnswers.stream()
                .collect(Collectors.groupingBy(AnswerRecord::getRecordId, Collectors.counting()));

        // 只保留有错题的考试
        List<Long> recordIdsWithWrong = wrongCountByRecord.keySet().stream().toList();

        // 4. 获取考试信息
        Set<Long> examIds = records.stream()
                .filter(r -> recordIdsWithWrong.contains(r.getId()))
                .map(ExamRecord::getExamId)
                .collect(Collectors.toSet());
        Map<Long, Exam> examMap = examIds.isEmpty() ? new HashMap<>() :
                examMapper.selectBatchIds(examIds).stream()
                        .collect(Collectors.toMap(Exam::getId, e -> e));

        // 5. 构建分组结果
        Map<Long, ExamRecord> recordMap = records.stream()
                .filter(r -> recordIdsWithWrong.contains(r.getId()))
                .collect(Collectors.toMap(ExamRecord::getId, r -> r));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : wrongCountByRecord.entrySet()) {
            Long recordId = entry.getKey();
            long wrongCount = entry.getValue();
            ExamRecord examRecord = recordMap.get(recordId);
            if (examRecord == null) continue;

            Exam exam = examMap.get(examRecord.getExamId());

            Map<String, Object> group = new LinkedHashMap<>();
            group.put("examRecordId", recordId);
            group.put("examTitle", exam != null ? exam.getTitle() : "未知考试");
            group.put("wrongCount", wrongCount);
            group.put("totalScore", examRecord.getScore());
            group.put("submitTime", examRecord.getSubmitTime());
            group.put("isPassed", examRecord.getIsPassed());
            result.add(group);
        }

        // 按提交时间降序
        // BUG-03修复：submitTime 可能为 null，使用 null-safe 比较
        result.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("submitTime");
            LocalDateTime timeB = (LocalDateTime) b.get("submitTime");
            if (timeB == null && timeA == null) return 0;
            if (timeB == null) return -1;
            if (timeA == null) return 1;
            return timeB.compareTo(timeA);
        });
        return result;
    }

    /**
     * 获取某次考试中的错题详情
     * @param examRecordId 考试记录ID
     * @param studentId 学生ID（权限校验）
     */
    public List<Map<String, Object>> getWrongQuestionsByExamRecord(Long examRecordId, Long studentId) {
        // 验证考试记录属于该学生
        ExamRecord examRecord = examRecordMapper.selectById(examRecordId);
        if (examRecord == null || !examRecord.getStudentId().equals(studentId)) {
            throw new BusinessException("无权查看此考试记录");
        }

        // 获取该考试下的所有错题
        List<AnswerRecord> wrongAnswers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getRecordId, examRecordId)
                        .eq(AnswerRecord::getIsCorrect, 0));

        if (wrongAnswers.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量获取题目
        List<Long> questionIds = wrongAnswers.stream()
                .map(AnswerRecord::getQuestionId).distinct().toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Map<String, Object>> result = new ArrayList<>();
        for (AnswerRecord answer : wrongAnswers) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("answerRecordId", answer.getId());
            item.put("questionId", question.getId());
            item.put("questionType", question.getType());
            item.put("questionTypeDesc", QuestionTypeEnum.fromCode(question.getType()).getDescription());
            item.put("content", question.getContent());
            item.put("optionA", question.getOptionA());
            item.put("optionB", question.getOptionB());
            item.put("optionC", question.getOptionC());
            item.put("optionD", question.getOptionD());
            item.put("studentAnswer", answer.getStudentAnswer());
            item.put("correctAnswer", question.getAnswer());
            item.put("analysis", question.getAnalysis());
            item.put("score", question.getScore());
            item.put("difficulty", question.getDifficulty());
            result.add(item);
        }

        return result;
    }

    // ==================== 错题练习 ====================

    /**
     * 创建错题练习
     */
    @Transactional
    public PracticeRecord createPractice(Long studentId, CreatePracticeDTO dto) {
        log.info("创建错题练习: studentId={}, dto={}", studentId, dto);

        List<Long> questionIds;

        if (dto.getQuestionIds() != null && !dto.getQuestionIds().isEmpty()) {
            questionIds = dto.getQuestionIds();
            log.info("使用 questionIds 创建练习: {}", questionIds);
        } else if (dto.getWrongAnswerIds() != null && !dto.getWrongAnswerIds().isEmpty()) {
            // 从answer_record ID提取questionId（去重）
            List<AnswerRecord> answers = answerRecordMapper.selectBatchIds(dto.getWrongAnswerIds());
            questionIds = answers.stream()
                    .map(AnswerRecord::getQuestionId)
                    .distinct()
                    .toList();
            log.info("使用 wrongAnswerIds 提取 questionIds: {}", questionIds);
        } else {
            throw new BusinessException("请至少选择一道错题进行练习");
        }

        if (questionIds.isEmpty()) {
            throw new BusinessException("未找到有效的错题");
        }

        // 获取题目信息并计算总分
        List<Question> questions = questionMapper.selectBatchIds(questionIds);
        if (questions.isEmpty()) {
            throw new BusinessException("未找到对应的题目，可能题目已被删除");
        }

        int totalScore = questions.stream()
                .mapToInt(q -> q.getScore() != null ? q.getScore() : 5)
                .sum();

        // 创建练习记录
        PracticeRecord practice = new PracticeRecord();
        practice.setStudentId(studentId);
        practice.setTitle(dto.getTitle() != null ? dto.getTitle() : "错题练习 " + LocalDateTime.now().toString().substring(0, 16));
        practice.setTotalCount(questions.size());
        practice.setCorrectCount(0);
        practice.setScore(0);
        practice.setTotalScore(totalScore);
        practice.setStatus(0);
        practice.setStartTime(LocalDateTime.now());

        try {
            practiceRecordMapper.insert(practice);
        } catch (Exception e) {
            log.error("插入 practice_record 失败，请确认数据库表已创建（执行 phase4-init.sql）", e);
            throw new BusinessException("创建练习失败，请确认数据库已初始化");
        }

        // 预插入练习题目关联记录（studentAnswer 为空表示未作答）
        Long practiceId = practice.getId();
        for (Question question : questions) {
            PracticeAnswer pa = new PracticeAnswer();
            pa.setPracticeId(practiceId);
            pa.setQuestionId(question.getId());
            practiceAnswerMapper.insert(pa);
        }

        log.info("创建错题练习成功: studentId={}, practiceId={}, questionCount={}", studentId, practiceId, questions.size());
        return practice;
    }

    /**
     * 获取练习详情（含题目和学生已保存的答案）
     */
    public Map<String, Object> getPracticeDetail(Long practiceId, Long studentId) {
        PracticeRecord practice = practiceRecordMapper.selectById(practiceId);
        if (practice == null || !practice.getStudentId().equals(studentId)) {
            throw new BusinessException("练习记录不存在");
        }

        // 获取练习的所有题目关联记录
        List<PracticeAnswer> practiceAnswers = practiceAnswerMapper.selectList(
                new LambdaQueryWrapper<PracticeAnswer>().eq(PracticeAnswer::getPracticeId, practiceId));

        if (practiceAnswers.isEmpty()) {
            throw new BusinessException("练习数据异常，未找到题目记录");
        }

        // 批量获取题目
        List<Long> questionIds = practiceAnswers.stream()
                .map(PracticeAnswer::getQuestionId)
                .distinct()
                .toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 构建有序题目列表（按 practice_answer 的插入顺序）
        List<Question> allQuestions = new ArrayList<>();
        for (PracticeAnswer pa : practiceAnswers) {
            Question q = questionMap.get(pa.getQuestionId());
            if (q != null && !allQuestions.contains(q)) {
                q.setPaperScore(q.getScore());
                q.setTypeDesc(QuestionTypeEnum.fromCode(q.getType()).getDescription());
                allQuestions.add(q);
            }
        }

        // 获取已保存的答案
        Map<Long, String> savedAnswers = new LinkedHashMap<>();
        for (PracticeAnswer pa : practiceAnswers) {
            if (pa.getStudentAnswer() != null && !pa.getStudentAnswer().isEmpty()) {
                savedAnswers.put(pa.getQuestionId(), pa.getStudentAnswer());
            }
        }

        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("practiceId", practice.getId());
        result.put("title", practice.getTitle());
        result.put("status", practice.getStatus());
        result.put("totalCount", practice.getTotalCount());
        result.put("questions", allQuestions);
        result.put("answers", savedAnswers);

        // 如果已完成，返回成绩信息
        if (practice.getStatus() == 1) {
            result.put("score", practice.getScore());
            result.put("correctCount", practice.getCorrectCount());
            result.put("totalScore", practice.getTotalScore());
            result.put("submitTime", practice.getSubmitTime());
        }

        return result;
    }

    /**
     * 保存练习答案
     */
    @Transactional
    public void savePracticeAnswer(Long practiceId, Long studentId, Long questionId, String answer) {
        PracticeRecord practice = validatePracticeOwnership(practiceId, studentId);
        if (practice.getStatus() != 0) {
            throw new BusinessException("练习已提交");
        }

        PracticeAnswer existing = practiceAnswerMapper.selectOne(
                new LambdaQueryWrapper<PracticeAnswer>()
                        .eq(PracticeAnswer::getPracticeId, practiceId)
                        .eq(PracticeAnswer::getQuestionId, questionId));

        if (existing != null) {
            existing.setStudentAnswer(answer);
            practiceAnswerMapper.updateById(existing);
        } else {
            PracticeAnswer pa = new PracticeAnswer();
            pa.setPracticeId(practiceId);
            pa.setQuestionId(questionId);
            pa.setStudentAnswer(answer);
            practiceAnswerMapper.insert(pa);
        }
    }

    /**
     * 提交练习并自动评分
     */
    @Transactional
    public PracticeRecord submitPractice(Long practiceId, Long studentId, Map<Long, String> answers) {
        PracticeRecord practice = validatePracticeOwnership(practiceId, studentId);
        if (practice.getStatus() != 0) {
            throw new BusinessException("练习已提交");
        }

        // 保存所有答案
        if (answers != null) {
            for (Map.Entry<Long, String> entry : answers.entrySet()) {
                savePracticeAnswer(practiceId, studentId, entry.getKey(), entry.getValue());
            }
        }

        // 获取所有答题记录和题目
        List<PracticeAnswer> allAnswers = practiceAnswerMapper.selectList(
                new LambdaQueryWrapper<PracticeAnswer>().eq(PracticeAnswer::getPracticeId, practiceId));

        List<Long> questionIds = allAnswers.stream()
                .map(PracticeAnswer::getQuestionId)
                .distinct()
                .toList();

        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        int totalScore = 0;
        int correctCount = 0;

        for (PracticeAnswer answer : allAnswers) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null || answer.getStudentAnswer() == null) {
                answer.setIsCorrect(0);
                answer.setScore(0);
                practiceAnswerMapper.updateById(answer);
                continue;
            }

            String studentAnswer = answer.getStudentAnswer().trim().toUpperCase();
            // 正确答案为空时（简答题/未设置答案），跳过自动评分，与 ExamService.autoGrade 保持一致
            if (question.getAnswer() == null) {
                answer.setIsCorrect(0);
                answer.setScore(0);
                practiceAnswerMapper.updateById(answer);
                continue;
            }
            String correctAnswer = question.getAnswer().trim().toUpperCase();
            int qScore = question.getScore() != null ? question.getScore() : 5;
            int qType = question.getType() != null ? question.getType() : 1;
            boolean isCorrect = false;
            int earnedScore = 0;

            if (qType == 2) {
                // 多选题：排序后比较
                String[] sArr = studentAnswer.split(",");
                String[] cArr = correctAnswer.split(",");
                if (sArr.length == cArr.length) {
                    Arrays.sort(sArr);
                    Arrays.sort(cArr);
                    if (Arrays.equals(sArr, cArr)) {
                        isCorrect = true;
                        earnedScore = qScore;
                    }
                }
            } else if (qType == 4) {
                // 主观题：练习模式不自动评分，标记为0
                isCorrect = false;
                earnedScore = 0;
            } else {
                // 单选/判断：精确匹配
                if (studentAnswer.equals(correctAnswer)) {
                    isCorrect = true;
                    earnedScore = qScore;
                }
            }

            totalScore += earnedScore;
            if (isCorrect) correctCount++;

            answer.setIsCorrect(isCorrect ? 1 : 0);
            answer.setScore(earnedScore);
            practiceAnswerMapper.updateById(answer);
        }

        // 更新练习记录
        practice.setScore(totalScore);
        practice.setCorrectCount(correctCount);
        practice.setTotalScore(questionMap.values().stream()
                .mapToInt(q -> q.getScore() != null ? q.getScore() : 5).sum());
        practice.setStatus(1);
        practice.setSubmitTime(LocalDateTime.now());
        practiceRecordMapper.updateById(practice);

        log.info("提交错题练习: studentId={}, practiceId={}, score={}/{}, correct={}/{}",
                studentId, practiceId, totalScore, practice.getTotalScore(), correctCount, practice.getTotalCount());

        return practice;
    }

    /**
     * 获取练习结果详情（含每题对错和解析）
     */
    public Map<String, Object> getPracticeResult(Long practiceId, Long studentId) {
        PracticeRecord practice = validatePracticeOwnership(practiceId, studentId);

        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(
                new LambdaQueryWrapper<PracticeAnswer>().eq(PracticeAnswer::getPracticeId, practiceId));

        List<Long> questionIds = answers.stream()
                .map(PracticeAnswer::getQuestionId)
                .distinct()
                .toList();

        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<Map<String, Object>> detailList = new ArrayList<>();
        for (PracticeAnswer answer : answers) {
            Question question = questionMap.get(answer.getQuestionId());
            if (question == null) continue;

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("questionId", question.getId());
            detail.put("questionType", question.getType());
            detail.put("questionTypeDesc", QuestionTypeEnum.fromCode(question.getType()).getDescription());
            detail.put("content", question.getContent());
            detail.put("optionA", question.getOptionA());
            detail.put("optionB", question.getOptionB());
            detail.put("optionC", question.getOptionC());
            detail.put("optionD", question.getOptionD());
            detail.put("studentAnswer", answer.getStudentAnswer());
            detail.put("correctAnswer", question.getAnswer());
            detail.put("isCorrect", answer.getIsCorrect());
            detail.put("score", answer.getScore());
            detail.put("analysis", question.getAnalysis());

            detailList.add(detail);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("practiceId", practice.getId());
        result.put("title", practice.getTitle());
        result.put("score", practice.getScore());
        result.put("totalScore", practice.getTotalScore());
        result.put("correctCount", practice.getCorrectCount());
        result.put("totalCount", practice.getTotalCount());
        result.put("accuracy", practice.getTotalCount() > 0
                ? Math.round(practice.getCorrectCount() * 100.0 / practice.getTotalCount() * 10) / 10.0 : 0);
        result.put("startTime", practice.getStartTime());
        result.put("submitTime", practice.getSubmitTime());
        result.put("details", detailList);

        return result;
    }

    /**
     * 获取学生的练习记录列表
     */
    public List<PracticeRecord> getPracticeHistory(Long studentId) {
        return practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getStudentId, studentId)
                        .orderByDesc(PracticeRecord::getCreateTime));
    }

    // ==================== 工具方法 ====================

    /**
     * 检查练习模块表是否存在
     */
    public Map<String, Object> checkPracticeReady() {
        Map<String, Object> result = new HashMap<>();
        boolean ready = true;
        try {
            practiceRecordMapper.selectCount(new LambdaQueryWrapper<>());
        } catch (Exception e) {
            log.error("practice_record 表不存在或不可用", e);
            ready = false;
            result.put("error", "practice_record 表不存在，请执行 backend/src/main/resources/db/phase4-init.sql");
        }
        result.put("ready", ready);
        return result;
    }

    private PracticeRecord validatePracticeOwnership(Long practiceId, Long studentId) {
        PracticeRecord practice = practiceRecordMapper.selectById(practiceId);
        if (practice == null) {
            throw new BusinessException("练习记录不存在");
        }
        if (!practice.getStudentId().equals(studentId)) {
            throw new BusinessException("无权访问此练习");
        }
        return practice;
    }
    // BUG-25修复：移除从未被调用的死代码方法 getQuestionsFromWrongAnswers
}
