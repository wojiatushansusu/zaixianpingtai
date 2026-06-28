package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.enums.ExamStatusEnum;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.common.enums.SubmitStatusEnum;
import com.exam.common.exception.BusinessException;
import com.exam.dto.SubmitExamDTO;
import com.exam.mapper.*;
import com.exam.pojo.*;
import com.exam.websocket.ExamMonitorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 考试核心业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamMapper examMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamWhitelistMapper examWhitelistMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final ExamOnlineStudentMapper examOnlineStudentMapper;
    private final FaceVerifyRecordMapper faceVerifyRecordMapper;
    private final ExamTokenMapper examTokenMapper;
    private final SelfStudyExamMapper selfStudyExamMapper;
    private final StringRedisTemplate redisTemplate;
    private final ExamMonitorWebSocketHandler examMonitorWebSocketHandler;

    // ==================== 学生端 ====================

    /**
     * 获取学生可参加的考试列表
     * 如果学生不在任何考试白名单中，自动将其加入所有活跃考试的名单（数据自修复）
     */
    public List<Exam> getStudentExams(Long studentId) {
        // 获取学生在白名单中的考试
        List<ExamWhitelist> whitelist = examWhitelistMapper.selectList(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getStudentId, studentId));

        List<Long> examIds = whitelist.stream()
                .map(ExamWhitelist::getExamId)
                .distinct()
                .collect(Collectors.toList());

        if (examIds.isEmpty()) {
            // 自修复：学生不在任何白名单中，自动加入所有存在的考试（排除已逻辑删除和AI自助考试）
            log.warn("getStudentExams: studentId={} 不在任何考试白名单中，尝试自动修复...", studentId);
            repairStudentWhitelist(studentId);

            // 修复后重新查询
            whitelist = examWhitelistMapper.selectList(
                    new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getStudentId, studentId));
            examIds = whitelist.stream()
                    .map(ExamWhitelist::getExamId)
                    .distinct()
                    .collect(Collectors.toList());

            if (examIds.isEmpty()) {
                log.warn("getStudentExams: 修复后仍无考试，可能系统中没有任何考试");
                return new ArrayList<>();
            }
            log.info("getStudentExams: 自动修复完成，studentId={} 现在有 {} 个考试", studentId, examIds.size());
        }

        List<Exam> exams = examMapper.selectList(
                new LambdaQueryWrapper<Exam>().in(Exam::getId, examIds).orderByDesc(Exam::getStartTime));

        // 预加载：该学生在这些考试中的所有答卷记录（含逻辑删除的不算，selectList默认排除deleted=1）
        Map<Long, List<ExamRecord>> recordsByExam = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .in(ExamRecord::getExamId, examIds)
                        .eq(ExamRecord::getStudentId, studentId))
                .stream()
                .collect(Collectors.groupingBy(ExamRecord::getExamId));

        // 预加载：哪些 examId 来自AI自助考试（selfStudyExam.examId → selfStudyExam.id）
        List<SelfStudyExam> selfStudyExams = selfStudyExamMapper.selectList(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .in(SelfStudyExam::getExamId, examIds)
                        .isNotNull(SelfStudyExam::getExamId)
                        .select(SelfStudyExam::getId, SelfStudyExam::getExamId));
        Map<Long, Long> examIdToSelfStudyId = selfStudyExams.stream()
                .collect(Collectors.toMap(SelfStudyExam::getExamId, SelfStudyExam::getId));

        exams.forEach(exam -> {
            // 填充试卷信息
            Paper paper = paperMapper.selectById(exam.getPaperId());
            if (paper != null) exam.setPaperTitle(paper.getTitle());

            // 获取该学生的答卷列表（按状态，取最近一条判断 submitStatus）
            List<ExamRecord> records = recordsByExam.getOrDefault(exam.getId(), Collections.emptyList());
            // 取最近一条的状态作为 submitStatus
            ExamRecord latest = records.stream()
                    .max(Comparator.comparing(r -> r.getSubmitTime() != null ? r.getSubmitTime() : r.getCreateTime()))
                    .orElse(null);
            exam.setSubmitStatus(latest != null ? String.valueOf(latest.getStatus()) : "0");
            // 已交卷的答卷次数（status != 0 才算正式提交）
            int submitted = (int) records.stream().filter(r -> r.getStatus() != 0).count();
            exam.setRecordCount(submitted);

            // 标记是否来自AI自助考试
            exam.setSelfStudyExamId(examIdToSelfStudyId.get(exam.getId()));
        });

        return exams;
    }

    /**
     * 获取考试详情（学生端）
     */
    public Map<String, Object> getExamDetail(Long examId, Long studentId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(5001, "考试不存在");
        }

        // 检查白名单
        Long count = examWhitelistMapper.selectCount(
                new LambdaQueryWrapper<ExamWhitelist>()
                        .eq(ExamWhitelist::getExamId, examId)
                        .eq(ExamWhitelist::getStudentId, studentId));
        if (count == 0) {
            throw new BusinessException(5005, "不在考试白名单中");
        }

        // 检查是否已提交
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId));

        Map<String, Object> result = new HashMap<>();
        result.put("exam", exam);
        result.put("submitted", record != null && record.getStatus() != 0);
        result.put("recordId", record != null ? record.getId() : null);

        return result;
    }

    /**
     * 开始考试 - 获取试卷内容
     *
     * 核心流程:
     * 1. 校验考试状态（时间、白名单、是否已提交）
     * 2. 创建或复用考试记录
     * 3. 从paper_question获取题目ID列表，批量查询题目详情
     * 4. 清除答案和解析（不泄露给考生）
     * 5. 恢复已保存的答案（断网续答场景）
     */
    public Map<String, Object> startExam(Long examId, Long studentId) {
        log.info("[startExam] 开始 examId={}, studentId={}", examId, studentId);

        // ─── 1. 校验考试 ───
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(5001, "考试不存在");
        }
        log.info("[startExam] 考试信息: title={}, status={}, paperId={}, startTime={}, endTime={}",
                exam.getTitle(), exam.getStatus(), exam.getPaperId(), exam.getStartTime(), exam.getEndTime());

        // 检查重考时间窗口（Redis缓存，教师批准重考时写入）
        String retakeKey = "exam:retake:" + examId + ":" + studentId;
        String retakeTimeStr = redisTemplate.opsForValue().get(retakeKey);
        LocalDateTime effectiveStart = exam.getStartTime();
        LocalDateTime effectiveEnd = exam.getEndTime();
        if (retakeTimeStr != null && retakeTimeStr.contains("|")) {
            String[] parts = retakeTimeStr.split("\\|");
            effectiveStart = LocalDateTime.parse(parts[0]);
            effectiveEnd = LocalDateTime.parse(parts[1]);
            log.info("[startExam] 使用重考时间窗口: {} ~ {}", effectiveStart, effectiveEnd);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(effectiveStart)) {
            long minutesToWait = java.time.Duration.between(now, effectiveStart).toMinutes() + 1;
            throw new BusinessException(5002, "考试未开始，请在" + minutesToWait + "分钟后重试");
        }
        if (now.isAfter(effectiveEnd)) {
            throw new BusinessException(5003, "考试已结束");
        }
        if (exam.getStatus() == 2 && retakeTimeStr == null) {
            throw new BusinessException(5003, "考试已结束");
        }
        if (exam.getStatus() != 1 && exam.getStatus() != 0 && retakeTimeStr == null) {
            throw new BusinessException(5006, "考试状态异常(status=" + exam.getStatus() + ")，请联系老师");
        }

        // 检查白名单
        Long wlCount = examWhitelistMapper.selectCount(
                new LambdaQueryWrapper<ExamWhitelist>()
                        .eq(ExamWhitelist::getExamId, examId)
                        .eq(ExamWhitelist::getStudentId, studentId));
        if (wlCount == 0) {
            throw new BusinessException(5005, "不在考试白名单中");
        }
        log.info("[startExam] 校验通过，开始加载试卷");

        // ─── 2. 获取或创建考试记录 ───
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId));

        if (record == null) {
            record = new ExamRecord();
            record.setExamId(examId);
            record.setStudentId(studentId);
            record.setStartTime(now);
            record.setStatus(0);
            record.setSwitchCount(0);
            // 防御性清理：物理删除可能存在的已删除记录，避免唯一索引冲突
            examRecordMapper.physicalDeleteDeleted(examId, studentId);
            examRecordMapper.insert(record);
            log.info("[startExam] 创建考试记录 recordId={}", record.getId());
        } else {
            log.info("[startExam] 复用已有考试记录 recordId={}, status={}", record.getId(), record.getStatus());
        }

        // ─── 3. 加载试卷题目（批量查询，避免N+1） ───
        if (exam.getPaperId() == null) {
            throw new BusinessException("考试未关联试卷，请联系老师");
        }

        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, exam.getPaperId())
                        .orderByAsc(PaperQuestion::getSortOrder));

        if (paperQuestions.isEmpty()) {
            log.error("[startExam] 试卷没有题目！paperId={}, examId={}", exam.getPaperId(), examId);
            throw new BusinessException("试卷没有题目，请联系老师检查试卷设置");
        }
        log.info("[startExam] 试卷题目关联记录: {} 条", paperQuestions.size());

        // 批量查询题目（一次SQL，消除N+1）
        List<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId)
                .distinct()
                .collect(Collectors.toList());
        List<Question> allQuestions = questionMapper.selectBatchIds(questionIds);
        Map<Long, Question> questionMap = allQuestions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 组装试卷题目列表（按paper_question的排序）
        List<Question> questions = new ArrayList<>();
        Map<Long, Integer> pqScoreMap = new HashMap<>();
        for (PaperQuestion pq : paperQuestions) {
            Question q = questionMap.get(pq.getQuestionId());
            if (q == null) {
                log.warn("[startExam] 题目不存在或已删除: questionId={}", pq.getQuestionId());
                continue; // 跳过已删除的题目
            }
            pqScoreMap.put(q.getId(), pq.getScore());
            q.setPaperScore(pq.getScore());
            q.setSortOrder(pq.getSortOrder());
            // 安全清除：不返回答案和解析
            q.setAnswer(null);
            q.setAnalysis(null);
            // 设置题型描述
            q.setTypeDesc(QuestionTypeEnum.fromCode(q.getType()).getDescription());
            questions.add(q);
        }

        if (questions.isEmpty()) {
            log.error("[startExam] 所有题目都已删除！paperId={}, questionIds={}", exam.getPaperId(), questionIds);
            throw new BusinessException("试卷中的题目已全部被删除，请联系老师");
        }
        log.info("[startExam] 有效题目: {} / {} (部分可能已删除)", questions.size(), paperQuestions.size());

        // ─── 4. 获取已保存的答案（断网续答场景） ───
        List<AnswerRecord> savedAnswers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, record.getId()));
        Map<Long, String> answerMap = savedAnswers.stream()
                .collect(Collectors.toMap(AnswerRecord::getQuestionId, AnswerRecord::getStudentAnswer));
        log.info("[startExam] 已保存答案: {} 条", answerMap.size());

        // ─── 5. 计算剩余时间 ───
        long remainingSeconds = calculateRemainingSeconds(effectiveEnd, record);

        // ─── 6. 构建返回数据 ───
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", record.getId());
        result.put("examTitle", exam.getTitle());
        result.put("duration", exam.getDuration());
        result.put("questions", questions);
        result.put("answers", answerMap);
        result.put("remainingTime", remainingSeconds);
        result.put("switchCount", record.getSwitchCount());
        result.put("maxSwitchCount", exam.getMaxSwitchCount());
        result.put("enableAiMonitor", exam.getEnableAiMonitor() != null ? exam.getEnableAiMonitor() : 1);

        log.info("[startExam] 完成 recordId={}, 题目数={}, 剩余时间={}秒", record.getId(), questions.size(), remainingSeconds);
        return result;
    }

    /**
     * 保存答案（实时保存，非提交）
     */
    @Transactional
    public void saveAnswer(Long examId, Long studentId, Long questionId, String answer) {
        ExamRecord record = getActiveRecord(examId, studentId);

        AnswerRecord existAnswer = answerRecordMapper.selectOne(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getRecordId, record.getId())
                        .eq(AnswerRecord::getQuestionId, questionId));

        if (existAnswer != null) {
            existAnswer.setStudentAnswer(answer);
            answerRecordMapper.updateById(existAnswer);
        } else {
            AnswerRecord answerRecord = new AnswerRecord();
            answerRecord.setRecordId(record.getId());
            answerRecord.setQuestionId(questionId);
            answerRecord.setStudentAnswer(answer);
            answerRecordMapper.insert(answerRecord);
        }
    }

    /**
     * 提交考试
     */
    @Transactional
    public ExamRecord submitExam(Long examId, Long studentId, SubmitExamDTO dto) {
        // 分布式锁防止并发重复提交
        String lockKey = "exam:submit:" + examId + ":" + studentId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 30, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("正在提交中，请勿重复操作");
        }

        try {
            ExamRecord record = getActiveRecord(examId, studentId);
            Exam exam = examMapper.selectById(examId);

            // 检查考试是否已结束（防止考试结束后仍能提交）
            if (exam != null && LocalDateTime.now().isAfter(exam.getEndTime())) {
                log.warn("考试已结束但仍尝试提交: examId={}, studentId={}", examId, studentId);
            }

            // 保存所有答案
            // BUG-01修复：dto 为 null 时（如强制交卷场景）跳过答案保存，使用已有答案
            if (dto != null && dto.getAnswers() != null) {
                for (Map.Entry<Long, String> entry : dto.getAnswers().entrySet()) {
                    saveAnswer(examId, studentId, entry.getKey(), entry.getValue());
                }
            }

            // 自动阅卷
            int score = autoGrade(record.getId(), exam.getPaperId());

            // 更新考试记录
            record.setSubmitTime(LocalDateTime.now());
            record.setScore(score);
            record.setStatus(1); // 已提交
            int passScore = exam.getPassScore() != null ? exam.getPassScore() : 60;
            record.setIsPassed(score >= passScore ? 1 : 0);
            examRecordMapper.updateById(record);

            // 缓存考试成绩到Redis
            redisTemplate.opsForValue().set(
                    "exam:score:" + examId + ":" + studentId,
                    String.valueOf(score),
                    24, TimeUnit.HOURS);

            return record;
        } finally {
            // 释放分布式锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 记录切屏违规（增强版）
     * - 原子增加切屏次数
     * - 增加违规严重度评分
     * - 超限立即强制交卷
     * - 通过WebSocket通知监考教师
     */
    public void recordSwitchScreen(Long examId, Long studentId) {
        ExamRecord record = getActiveRecord(examId, studentId);
        if (record == null) return;

        // 原子增加切屏次数（避免并发竞态条件）
        examRecordMapper.incrementSwitchCount(record.getId());
        
        // BUG-15修复：重新从数据库查询最新值
        ExamRecord updatedRecord = examRecordMapper.selectById(record.getId());
        int newSwitchCount = updatedRecord != null ? updatedRecord.getSwitchCount() : (record.getSwitchCount() + 1);

        Exam exam = examMapper.selectById(examId);
        int maxSwitch = exam != null ? exam.getMaxSwitchCount() : 3;
        int remaining = Math.max(0, maxSwitch - newSwitchCount);

        // 计算严重度: 接近上限时严重度更高
        int severity = 1;
        if (newSwitchCount >= maxSwitch) severity = 3;         // 超限: 最高级
        else if (remaining <= 1) severity = 2;               // 还剩1次: 中高级
        else if (remaining <= 2) severity = 2;               // 还剩2次: 中级

        // 记录违规（带severity和score）
        ViolationRecord violation = new ViolationRecord();
        violation.setExamId(examId);
        violation.setStudentId(studentId);
        violation.setViolationType("switch_screen");
        violation.setDescription("第" + newSwitchCount + "次切屏(剩余" + remaining + "次)");
        violation.setSeverity(severity);
        violation.setOccurTime(LocalDateTime.now());
        try {
            violation.setScore(calculateViolationScore("switch_screen", severity));
        } catch (Exception e) {
            // score字段可能不存在，忽略
        }
        violationRecordMapper.insert(violation);

        // 通过WebSocket通知监考教师
        notifyViolationToMonitor(examId, studentId, "switch_screen", 
            "第" + newSwitchCount + "次切屏(剩余" + remaining + "次)", severity);

        // 检查是否超过最大切屏次数 → 立即强制交卷
        if (newSwitchCount > maxSwitch) {
            forceSubmitForViolation(examId, studentId, record, "切屏次数超限(" + maxSwitch + "次)");
        }
    }

    /**
     * 计算违规扣分
     * switch_screen: 每次扣5分，越接近上限扣越多
     * no_face: 每次扣10分
     * multi_face: 每次扣15分
     * look_away: 每次扣3分
     * forbidden_item: 每次扣20分
     */
    private int calculateViolationScore(String violationType, int severity) {
        switch (violationType) {
            case "switch_screen": return 5 * severity;
            case "no_face": return 10 * severity;
            case "multi_face": return 15 * severity;
            case "look_away": return 3 * severity;
            case "forbidden_item": return 20 * severity;
            case "keyboard_violation": return 5 * severity;
            case "face_position_changed": return 15;  // 替考嫌疑
            default: return severity * 3;
        }
    }

    /**
     * 违规时通过WebSocket通知监考教师
     */
    private void notifyViolationToMonitor(Long examId, Long studentId, String type, 
                                           String description, int severity) {
        try {
            Map<String, Object> alertData = Map.of(
                "examId", examId,
                "studentId", studentId,
                "violationType", type,
                "description", description,
                "severity", severity,
                "timestamp", System.currentTimeMillis()
            );
            examMonitorWebSocketHandler.broadcastToExamRoom(examId, "violation_alert", alertData, "teacher");
        } catch (Exception e) {
            log.warn("WebSocket通知监考教师失败: {}", e.getMessage());
        }
    }

    /**
     * 因违规强制交卷（通用方法）
     */
    private void forceSubmitForViolation(Long examId, Long studentId, ExamRecord record, String reason) {
        SubmitExamDTO dto = new SubmitExamDTO();
        dto.setExamId(examId);
        List<AnswerRecord> answers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, record.getId()));
        Map<Long, String> answerMap = answers.stream()
                .collect(Collectors.toMap(AnswerRecord::getQuestionId, AnswerRecord::getStudentAnswer));
        dto.setAnswers(answerMap);

        ExamRecord submitted = submitExam(examId, studentId, dto);
        submitted.setStatus(2); // 自动提交标记
        examRecordMapper.updateById(submitted);

        log.warn("[违规强制交卷] examId={}, studentId={}, reason={}", examId, studentId, reason);
    }

    /**
     * 获取学生成绩列表
     */
    public List<ExamRecord> getStudentGrades(Long studentId) {
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getSubmitTime));

        records.forEach(r -> {
            Exam exam = examMapper.selectById(r.getExamId());
            if (exam != null) r.setExamTitle(exam.getTitle());
        });

        return records;
    }

    // ==================== 自动阅卷 ====================

    /**
     * 自动阅卷（客观题）
     */
    private int autoGrade(Long recordId, Long paperId) {
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, paperId)
                        .orderByAsc(PaperQuestion::getSortOrder));

        List<AnswerRecord> answers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, recordId));
        Map<Long, AnswerRecord> answerMap = answers.stream()
                .collect(Collectors.toMap(AnswerRecord::getQuestionId, a -> a));

        int totalScore = 0;
        for (PaperQuestion pq : paperQuestions) {
            Question question = questionMapper.selectById(pq.getQuestionId());
            AnswerRecord answerRecord = answerMap.get(pq.getQuestionId());

            int questionScore = 0;
            boolean isCorrect = false;

            if (answerRecord != null && answerRecord.getStudentAnswer() != null && question != null) {
                String studentAnswer = answerRecord.getStudentAnswer().trim().toUpperCase();
                // BUG-07修复：correctAnswer 可能为 null（简答题/未设置答案），加空值保护
                if (question.getAnswer() == null) {
                    // 简答题等没有标准答案的题目，跳过自动评分
                    if (answerRecord != null) {
                        answerRecord.setIsCorrect(0);
                        answerRecord.setScore(0);
                        answerRecordMapper.updateById(answerRecord);
                    }
                    continue;
                }
                String correctAnswer = question.getAnswer().trim().toUpperCase();

                // 根据题目类型区分评分逻辑
                int qType = question.getType() != null ? question.getType() : 1;

                if (qType == 2) {
                    // 多选题（type=2）：排序后比较，允许多种顺序
                    String[] sArr = studentAnswer.split(",");
                    String[] cArr = correctAnswer.split(",");
                    if (sArr.length == cArr.length) {
                        java.util.Arrays.sort(sArr);
                        java.util.Arrays.sort(cArr);
                        if (java.util.Arrays.equals(sArr, cArr)) {
                            isCorrect = true;
                            questionScore = pq.getScore();
                        }
                    }
                } else {
                    // 单选题（type=1）和判断题（type=3）：精确匹配
                    if (studentAnswer.equals(correctAnswer)) {
                        isCorrect = true;
                        questionScore = pq.getScore();
                    }
                }
            }

            totalScore += questionScore;

            if (answerRecord != null) {
                answerRecord.setIsCorrect(isCorrect ? 1 : 0);
                answerRecord.setScore(questionScore);
                answerRecordMapper.updateById(answerRecord);
            }
        }

        return totalScore;
    }

    // ==================== 教师端 ====================

    /**
     * 获取考试成绩统计
     */
    public Map<String, Object> getExamStatistics(Long examId) {
        Exam exam = examMapper.selectById(examId);

        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .ne(ExamRecord::getStatus, 0));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalStudents", records.size());

        if (!records.isEmpty()) {
            double avg = records.stream().mapToInt(ExamRecord::getScore).average().orElse(0);
            int max = records.stream().mapToInt(ExamRecord::getScore).max().orElse(0);
            int min = records.stream().mapToInt(ExamRecord::getScore).min().orElse(0);
            long passCount = records.stream().filter(r -> r.getIsPassed() == 1).count();

            stats.put("avgScore", Math.round(avg * 100.0) / 100.0);
            stats.put("maxScore", max);
            stats.put("minScore", min);
            stats.put("passCount", passCount);
            stats.put("passRate", Math.round(passCount * 100.0 / records.size() * 100) / 100.0);
        } else {
            stats.put("avgScore", 0);
            stats.put("maxScore", 0);
            stats.put("minScore", 0);
            stats.put("passCount", 0);
            stats.put("passRate", 0);
        }

        if (exam != null) {
            Paper paper = paperMapper.selectById(exam.getPaperId());
            stats.put("passScore", paper != null ? paper.getPassScore() : 60);
            stats.put("totalScore", paper != null ? paper.getTotalScore() : 100);
        }

        return stats;
    }

    // ==================== 定时任务 ====================

    /**
     * 定时更新考试状态（每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void updateExamStatus() {
        LocalDateTime now = LocalDateTime.now();

        // 清理过期且已使用的ExamToken（P2: 防止表膨胀）
        try {
            examTokenMapper.delete(new LambdaQueryWrapper<ExamToken>()
                    .eq(ExamToken::getUsed, 1)
                    .lt(ExamToken::getExpireTime, now.minusDays(7)));
        } catch (Exception e) {
            log.warn("清理过期ExamToken失败: {}", e.getMessage());
        }

        // 将未开始且已到开始时间的考试设为进行中
        examMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Exam>()
                        .eq(Exam::getStatus, 0)
                        .le(Exam::getStartTime, now)
                        .set(Exam::getStatus, 1));

        // 将进行中且已到结束时间的考试设为已结束
        List<Exam> endingExams = examMapper.selectList(
                new LambdaQueryWrapper<Exam>()
                        .eq(Exam::getStatus, 1)
                        .le(Exam::getEndTime, now));

        for (Exam exam : endingExams) {
            exam.setStatus(2);
            examMapper.updateById(exam);

            // 自动提交所有未提交的答卷
            autoSubmitExpired(exam);
        }

        log.debug("考试状态更新完成");
    }

    /**
     * 自动提交超时答卷
     */
    private void autoSubmitExpired(Exam exam) {
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, exam.getId())
                        .eq(ExamRecord::getStatus, 0));

        for (ExamRecord record : records) {
            SubmitExamDTO dto = new SubmitExamDTO();
            dto.setExamId(exam.getId());

            List<AnswerRecord> answers = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, record.getId()));
            Map<Long, String> answerMap = answers.stream()
                    .collect(Collectors.toMap(AnswerRecord::getQuestionId, AnswerRecord::getStudentAnswer));
            dto.setAnswers(answerMap);

            // BUG-02修复：submitExam 内部已将 status 设为1并保存，
            // 使用返回值（新鲜对象）再将 status 改为2（自动提交），避免旧对象覆盖 score/isPassed
            ExamRecord submitted = submitExam(exam.getId(), record.getStudentId(), dto);
            submitted.setStatus(2); // 自动提交
            examRecordMapper.updateById(submitted);
        }
    }

    // ==================== 工具方法 ====================

    private ExamRecord getActiveRecord(Long examId, Long studentId) {
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId));

        if (record == null) {
            throw new BusinessException("请先开始考试");
        }
        if (record.getStatus() != 0) {
            throw new BusinessException(5004, "答卷已提交");
        }

        return record;
    }

    private long calculateRemainingSeconds(LocalDateTime effectiveEnd, ExamRecord record) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 距考试截止时间的剩余秒数
        long remainByEnd = java.time.Duration.between(now, effectiveEnd).getSeconds();

        // 2. 按考试时长（duration分钟）计算剩余秒数：duration×60 - 已答题秒数
        //    exam.duration 通过调用方传入，这里从 record 的 startTime 推算；
        //    若 record 中没有 examId 关联的 duration，则退化为只用截止时间
        long remainByDuration = remainByEnd; // 默认与截止时间一致（普通考试兜底）
        if (record.getStartTime() != null) {
            // 获取该考试的 duration（分钟）
            Exam examForDuration = examMapper.selectById(record.getExamId());
            if (examForDuration != null && examForDuration.getDuration() != null) {
                long totalSeconds = (long) examForDuration.getDuration() * 60;
                long elapsedSeconds = java.time.Duration.between(record.getStartTime(), now).getSeconds();
                remainByDuration = totalSeconds - elapsedSeconds;
            }
        }

        // 取两者最小值，保证不超过 duration 也不超过截止时间
        long remaining = Math.min(remainByEnd, remainByDuration);
        return Math.max(0, remaining);
    }




    // ==================== 教师/管理员 - 删除考试记录 ====================

    /**
     * 删除考试及其所有关联数据（考试记录、答题记录、违规记录、在线状态、人脸核验、白名单等）
     * @param examId 考试ID
     */
    @Transactional
    public void deleteExamWithRecords(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(5001, "考试不存在");
        }

        // 1. 查找该考试的所有考试记录
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId));

        for (ExamRecord record : records) {
            // 2. 删除答题记录
            answerRecordMapper.delete(
                    new LambdaQueryWrapper<AnswerRecord>().eq(AnswerRecord::getRecordId, record.getId()));
            // 3. 删除违规记录
            violationRecordMapper.delete(
                    new LambdaQueryWrapper<ViolationRecord>()
                            .eq(ViolationRecord::getExamId, examId)
                            .eq(ViolationRecord::getStudentId, record.getStudentId()));
            // 4. 删除在线状态记录
            examOnlineStudentMapper.delete(
                    new LambdaQueryWrapper<ExamOnlineStudent>()
                            .eq(ExamOnlineStudent::getExamId, examId)
                            .eq(ExamOnlineStudent::getStudentId, record.getStudentId()));
            // 5. 清除Redis缓存
            redisTemplate.delete("exam:score:" + examId + ":" + record.getStudentId());
        }

        // 6. 删除考试记录
        examRecordMapper.delete(
                new LambdaQueryWrapper<ExamRecord>().eq(ExamRecord::getExamId, examId));

        // 7. 删除人脸核验记录
        faceVerifyRecordMapper.delete(
                new LambdaQueryWrapper<FaceVerifyRecord>().eq(FaceVerifyRecord::getExamId, examId));

        // 8. 删除考试凭证
        examTokenMapper.delete(
                new LambdaQueryWrapper<ExamToken>().eq(ExamToken::getExamId, examId));

        // 9. 删除白名单
        examWhitelistMapper.delete(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, examId));

        // 10. 删除考试本身
        examMapper.deleteById(examId);

        log.info("删除考试及所有关联数据: examId={}, 记录数={}", examId, records.size());
    }

    // ==================== 数据修复 ====================

    /**
     * 修复学生白名单数据
     * 当学生不在任何考试白名单中时，自动将其加入所有活跃考试（排除已逻辑删除和AI自助考试）
     */
    @Transactional
    public void repairStudentWhitelist(Long studentId) {
        // 1. 查询所有活跃考试（非逻辑删除）
        List<Exam> allExams = examMapper.selectList(
                new LambdaQueryWrapper<Exam>().orderByDesc(Exam::getCreateTime));

        if (allExams.isEmpty()) {
            log.warn("repairStudentWhitelist: 系统中没有任何考试");
            return;
        }

        // 2. 排除AI自助考试（self_study_exam表中有记录的）
        List<SelfStudyExam> selfStudyExams = selfStudyExamMapper.selectList(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .isNotNull(SelfStudyExam::getExamId)
                        .select(SelfStudyExam::getExamId));
        Set<Long> selfStudyExamIds = selfStudyExams.stream()
                .map(SelfStudyExam::getExamId).collect(Collectors.toSet());

        // 3. 对每个非AI自助考试，尝试加入白名单
        int addedCount = 0;
        for (Exam exam : allExams) {
            if (selfStudyExamIds.contains(exam.getId())) {
                continue;
            }

            try {
                // 检查是否已在白名单中（避免唯一索引冲突）
                Long exists = examWhitelistMapper.selectCount(
                        new LambdaQueryWrapper<ExamWhitelist>()
                                .eq(ExamWhitelist::getExamId, exam.getId())
                                .eq(ExamWhitelist::getStudentId, studentId));
                if (exists > 0) {
                    continue;
                }

                ExamWhitelist wl = new ExamWhitelist();
                wl.setExamId(exam.getId());
                wl.setStudentId(studentId);
                examWhitelistMapper.insert(wl);
                addedCount++;
                log.info("repairStudentWhitelist: 已将学生 {} 加入考试 {} 的白名单", studentId, exam.getId());
            } catch (Exception e) {
                log.warn("repairStudentWhitelist: 添加白名单失败 examId={}, error={}", exam.getId(), e.getMessage());
            }
        }

        log.info("repairStudentWhitelist: 学生 {} 已被加入 {} 个考试的白名单", studentId, addedCount);
    }
}
