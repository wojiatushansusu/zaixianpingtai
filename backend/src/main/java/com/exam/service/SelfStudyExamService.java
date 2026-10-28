package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.common.exception.BusinessException;
import com.exam.mapper.*;
import com.exam.pojo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * AI自助考试服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfStudyExamService {

    private final SelfStudyExamMapper selfStudyExamMapper;
    private final SelfStudyParticipantMapper participantMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final ExamRecordMapper examRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final AiPaperGeneratorService aiPaperGeneratorService;
    private final ExamMapper examMapper;
    private final ExamWhitelistMapper examWhitelistMapper;
    private final WrongQuestionService wrongQuestionService;

    // ==================== 创建与生成 ====================

    /**
     * 生成唯一6位考试码
     */
    /**
     * 生成结果：考试对象 + 出卷质量报告。
     *
     * <p>质量报告原本只写日志，用户看不到「有 2 道题因结构不合格被剔除」，
     * 也不知道哪些题答案存疑。这里一并回传给前端，让 AI 出卷的结果是可见的。
     */
    public record GenerateOutcome(SelfStudyExam exam, Map<String, Object> quality) {
    }

    /**
     * 把生成器的校验结果整理成可直接给前端用的结构。
     * 存疑题号由 0 基转成 1 基，避免前端再 +1。
     */
    private Map<String, Object> buildQuality(AiPaperGeneratorService.GenerateResult result) {
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("generated", result.getGeneratedCount());
        quality.put("accepted", result.getQuestions().size());
        quality.put("rejected", result.getRejectedCount());
        quality.put("warnings", result.getWarnings());
        quality.put("suspectQuestions", result.getSuspectIndexes().stream()
                .map(i -> i + 1)
                .collect(Collectors.toList()));
        return quality;
    }

    public String generateExamCode() {
        for (int i = 0; i < 100; i++) {
            String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
            Long count = selfStudyExamMapper.selectCount(
                    new LambdaQueryWrapper<SelfStudyExam>()
                            .eq(SelfStudyExam::getExamCode, code));
            if (count == 0) {
                return code;
            }
        }
        throw new BusinessException("生成考试码失败，请稍后重试");
    }

    /**
     * 创建AI自助考试
     * 1. 创建自助考试记录
     * 2. 调用AI生成题目
     * 3. 将题目存入question表
     * 4. 创建paper和paper_question
     * 5. 添加创建者为参与者
     */
    @Transactional
    public SelfStudyExam createSelfStudyExam(Long studentId, String prompt, Integer duration) {
        return createSelfStudyExam(studentId, prompt, duration, null);
    }

    /**
     * @param stageKey 所属复习阶段，可为空。从「复习路径」发起时传入，用于结果回填。
     */
    public SelfStudyExam createSelfStudyExam(Long studentId, String prompt, Integer duration, String stageKey) {
        // 创建自助考试记录
        String examCode = generateExamCode();
        String title = "AI自助考试-" + examCode;

        SelfStudyExam exam = new SelfStudyExam();
        exam.setTitle(title);
        exam.setDescription(prompt);
        exam.setCreatorId(studentId);
        exam.setExamCode(examCode);
        exam.setDuration(duration != null ? duration : 60);
        exam.setTotalScore(100);
        exam.setPassScore(60);
        exam.setStatus(0); // 生成中
        exam.setQuestionCount(0);
        exam.setStudentCount(1);
        exam.setGeneratePrompt(prompt);
        exam.setStageKey(stageKey);
        selfStudyExamMapper.insert(exam);

        log.info("创建自助考试记录: id={}, examCode={}", exam.getId(), examCode);

        // 添加创建者为参与者
        SelfStudyParticipant participant = new SelfStudyParticipant();
        participant.setExamId(exam.getId());
        participant.setStudentId(studentId);
        participant.setIsCreator(1);
        participant.setJoinTime(LocalDateTime.now());
        participantMapper.insert(participant);

        return exam;
    }

    /**
     * 依据某次考试的错题，创建一份专项巩固卷（只创建记录，生成仍走 /generate）。
     *
     * <p>这是「错题 → 再考一次」的入口。之前错题本只能走「错题练习」，与 AI 出卷是两条独立的路；
     * 这里把两者接上：错题本身就是最好的出题素材，比让用户自己想提示词强得多。
     *
     * <p>提示词由服务端组装，因为服务端才有完整的题干、选项与正确答案。
     */
    @Transactional
    public SelfStudyExam createFromWrongQuestions(Long studentId, Long examRecordId, Integer duration) {
        if (examRecordId == null) {
            throw new BusinessException("缺少考试记录ID");
        }

        List<Map<String, Object>> wrongQuestions =
                wrongQuestionService.getWrongQuestionsByExamRecord(examRecordId, studentId);

        if (wrongQuestions.isEmpty()) {
            throw new BusinessException("这次考试没有错题，暂时不需要专项巩固");
        }

        String prompt = buildWrongQuestionPrompt(wrongQuestions);
        log.info("按错题创建专项卷: studentId={}, examRecordId={}, wrongCount={}",
                studentId, examRecordId, wrongQuestions.size());

        return createSelfStudyExam(studentId, prompt, duration);
    }

    /**
     * 把错题整理成给模型的出题要求。
     *
     * 题量取「错题数的两倍」，最少 5 道、最多 20 道：只有一个错题时也能练到足够量，
     * 错题太多时则截断，避免提示词过长导致模型跑偏。
     */
    private String buildWrongQuestionPrompt(List<Map<String, Object>> wrongQuestions) {
        int wrongCount = wrongQuestions.size();
        int targetCount = Math.min(Math.max(wrongCount * 2, 5), 20);

        StringBuilder sb = new StringBuilder();
        sb.append("请根据我这次考试的错题，出一份专项巩固卷。\n")
          .append("要求：\n")
          .append("1. 共 ").append(targetCount).append(" 道题，全部为客观题（单选/多选/判断），不要出简答题\n")
          .append("2. 考点与下面这些错题相同或相近，但必须换一道新题，不能照抄原题\n")
          .append("3. 难度与原错题相当，错误选项要有干扰性\n")
          .append("4. 每道题都要给出解析\n\n")
          .append("这次做错的题目如下：\n");

        int listed = Math.min(wrongCount, 20);
        for (int i = 0; i < listed; i++) {
            Map<String, Object> q = wrongQuestions.get(i);
            sb.append(i + 1).append(". [").append(q.get("questionTypeDesc")).append("] ")
              .append(q.get("content")).append("\n");

            for (String key : List.of("optionA", "optionB", "optionC", "optionD")) {
                Object option = q.get(key);
                if (option != null && !String.valueOf(option).isBlank()) {
                    sb.append("   ").append(key.substring("option".length()))
                      .append(". ").append(option).append("\n");
                }
            }

            sb.append("   我的答案：").append(q.get("studentAnswer"))
              .append("，正确答案：").append(q.get("correctAnswer")).append("\n");
        }

        if (wrongCount > listed) {
            sb.append("（其余 ").append(wrongCount - listed).append(" 道错题从略）\n");
        }

        return sb.toString();
    }

    /**
     * AI生成题目并完成考试创建（异步调用后由前端轮询结果）
     */
    @Transactional
    public GenerateOutcome generateAndUpdateExam(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (!exam.getCreatorId().equals(studentId)) {
            throw new BusinessException("只有创建者可以触发生成");
        }
        if (exam.getStatus() != 0) {
            throw new BusinessException("该考试已生成或正在处理中");
        }

        // 调用AI生成题目
        AiPaperGeneratorService.GenerateResult result =
                aiPaperGeneratorService.generatePaper(exam.getGeneratePrompt(), studentId);

        if (!result.isSuccess()) {
            // 生成失败，标记状态
            exam.setStatus(3);
            exam.setDescription("生成失败: " + result.getError());
            selfStudyExamMapper.updateById(exam);
            throw new BusinessException(result.getError());
        }

        // 保存题目到question表
        List<Question> questions = new ArrayList<>();
        for (Map<String, Object> qData : result.getQuestions()) {
            Question q = new Question();
            q.setType((Integer) qData.get("type"));
            q.setContent((String) qData.get("content"));
            q.setOptionA((String) qData.get("optionA"));
            q.setOptionB((String) qData.get("optionB"));
            q.setOptionC((String) qData.get("optionC"));
            q.setOptionD((String) qData.get("optionD"));
            q.setAnswer((String) qData.get("answer"));
            q.setAnalysis((String) qData.get("analysis"));
            q.setScore((Integer) qData.getOrDefault("score", 10));
            q.setDifficulty((Integer) qData.getOrDefault("difficulty", 2));
            String topic = (String) qData.get("topic");
            if (topic != null && !topic.isBlank()) {
                q.setKnowledgePoint(topic.length() > 64 ? topic.substring(0, 64) : topic);
            }
            q.setCreatorId(studentId);
            questionMapper.insert(q);
            questions.add(q);
        }

        // 计算总分和分值
        // BUG-05修复：原代码 if 分支体与分支外语句完全相同是死代码，
        // 修复为：整除时每题分值 = totalScore/size，不整除时最后一题补差值
        int totalScore = 100;
        int perScore = questions.size() > 0 ? totalScore / questions.size() : 10;
        int lastScore = questions.size() > 0 ? totalScore - perScore * (questions.size() - 1) : 10;

        // 创建试卷
        Paper paper = new Paper();
        paper.setTitle(result.getTitle());
        paper.setDescription("AI自动生成 - " + exam.getGeneratePrompt());
        paper.setTotalScore(totalScore);  // BUG-05修复：直接用 totalScore=100，不是 perScore*size
        paper.setPassScore(60);
        paper.setQuestionCount(questions.size());
        paper.setCreatorId(studentId);
        paperMapper.insert(paper);

        // 关联试卷和题目
        for (int i = 0; i < questions.size(); i++) {
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(questions.get(i).getId());
            // BUG-05修复：最后一题分值 = 总分 - 前面所有题分值之和，保证总分精确为100
            pq.setScore(i == questions.size() - 1 ? lastScore : perScore);
            pq.setSortOrder(i + 1);
            paperQuestionMapper.insert(pq);
        }

        // 更新自助考试记录
        exam.setTitle(result.getTitle());
        exam.setPaperId(paper.getId());
        exam.setTotalScore(totalScore);  // BUG-05修复：直接用 totalScore=100
        exam.setPassScore(60);
        exam.setQuestionCount(questions.size());
        exam.setStatus(1); // 待考试

        // 创建正式的Exam记录，复用现有考试流程
        // 时间设为"至今后7天有效"（自助考试无严格时间限制，但需要设置endTime）
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusDays(7);

        Exam realExam = new Exam();
        realExam.setTitle(result.getTitle() + " [" + exam.getExamCode() + "]");
        realExam.setDescription("AI自助考试，考试码: " + exam.getExamCode());
        realExam.setPaperId(paper.getId());
        realExam.setCreatorId(studentId);
        realExam.setStartTime(startTime);
        realExam.setEndTime(endTime);
        realExam.setDuration(exam.getDuration());
        realExam.setStatus(1); // 进行中（允许随时开始）
        realExam.setMaxSwitchCount(10); // 宽松限制
        realExam.setRequireFaceVerify(0); // 不需要人脸核验
        realExam.setEnableAiMonitor(0); // AI自助考试不启用AI监考
        realExam.setPassScore(60);
        examMapper.insert(realExam);

        exam.setExamId(realExam.getId());
        selfStudyExamMapper.updateById(exam);

        // 将创建者加入白名单
        addToWhitelist(realExam.getId(), studentId);

        log.info("AI自助考试生成成功: id={}, title={}, questions={}, paperId={}, examId={}",
                exam.getId(), result.getTitle(), questions.size(), paper.getId(), realExam.getId());

        return new GenerateOutcome(exam, buildQuality(result));
    }

    // ==================== 考试码加入 ====================

    /**
     * 通过考试码加入考试
     */
    @Transactional
    public SelfStudyExam joinByExamCode(String examCode, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectOne(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .eq(SelfStudyExam::getExamCode, examCode)
                        .eq(SelfStudyExam::getStatus, 1)); // 只有待考试状态才能加入

        if (exam == null) {
            throw new BusinessException("考试码无效或考试已开始/结束");
        }

        // 检查是否已加入
        Long existCount = participantMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getExamId, exam.getId())
                        .eq(SelfStudyParticipant::getStudentId, studentId));
        if (existCount > 0) {
            throw new BusinessException("你已经加入了该考试");
        }

        // 防御性清理唯一索引冲突
        participantMapper.physicalDeleteDeleted(exam.getId(), studentId);

        // 添加参与者
        SelfStudyParticipant participant = new SelfStudyParticipant();
        participant.setExamId(exam.getId());
        participant.setStudentId(studentId);
        participant.setIsCreator(0);
        participant.setJoinTime(LocalDateTime.now());
        participantMapper.insert(participant);

        // 更新参与人数
        exam.setStudentCount(exam.getStudentCount() + 1);
        selfStudyExamMapper.updateById(exam);

        // 将新加入者加入Exam白名单
        if (exam.getExamId() != null) {
            addToWhitelist(exam.getExamId(), studentId);
        }

        log.info("学生加入自助考试: examId={}, studentId={}, examCode={}", exam.getId(), studentId, examCode);
        return exam;
    }

    // ==================== 学生端操作 ====================

    /**
     * 获取我参与的自助考试列表
     */
    public List<SelfStudyExam> getMySelfStudyExams(Long studentId) {
        List<SelfStudyParticipant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getStudentId, studentId));

        if (participants.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> examIds = participants.stream()
                .map(SelfStudyParticipant::getExamId)
                .collect(Collectors.toList());

        List<SelfStudyExam> exams = selfStudyExamMapper.selectList(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .in(SelfStudyExam::getId, examIds)
                        .orderByDesc(SelfStudyExam::getCreateTime));

        // 填充试卷标题和当前学生的提交状态
        exams.forEach(exam -> {
            if (exam.getPaperId() != null) {
                Paper paper = paperMapper.selectById(exam.getPaperId());
                if (paper != null) exam.setPaperTitle(paper.getTitle());
            }

            // 检查是否已提交（使用关联的正式考试ID查询ExamRecord）
            if (exam.getExamId() != null) {
                Long recordCount = examRecordMapper.selectCount(
                        new LambdaQueryWrapper<ExamRecord>()
                                .eq(ExamRecord::getExamId, exam.getExamId())
                                .eq(ExamRecord::getStudentId, studentId)
                                .ne(ExamRecord::getStatus, 0));
                exam.setMySubmitStatus(recordCount > 0 ? 1 : 0);
            }
        });

        return exams;
    }

    /**
     * 获取自助考试详情
     */
    public Map<String, Object> getExamDetail(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        // 验证是否为参与者
        Long pCount = participantMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getExamId, examId)
                        .eq(SelfStudyParticipant::getStudentId, studentId));
        if (pCount == 0) {
            throw new BusinessException("你不是该考试的参与者");
        }

        // 填充试卷标题
        if (exam.getPaperId() != null) {
            Paper paper = paperMapper.selectById(exam.getPaperId());
            if (paper != null) exam.setPaperTitle(paper.getTitle());
        }

        // 检查提交状态
        // BUG-16修复：ExamRecord 存储的是正式 Exam 的 examId（即 exam.getExamId()），
        // 而非 SelfStudyExam 的主键 examId，否则永远查不到提交记录
        ExamRecord record = null;
        if (exam.getExamId() != null) {
            record = examRecordMapper.selectOne(
                    new LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, exam.getExamId())
                            .eq(ExamRecord::getStudentId, studentId));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("exam", exam);
        result.put("submitted", record != null && record.getStatus() != 0);
        result.put("recordId", record != null ? record.getId() : null);

        return result;
    }

    /**
     * 发布考试（创建者操作：将状态从"待考试"变为"进行中"）
     */
    @Transactional
    public void publishExam(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (!exam.getCreatorId().equals(studentId)) {
            throw new BusinessException("只有创建者可以发布考试");
        }
        if (exam.getStatus() != 1) {
            throw new BusinessException("当前状态不允许发布");
        }

        exam.setStatus(2); // 进行中
        selfStudyExamMapper.updateById(exam);

        log.info("自助考试已发布: examId={}, examCode={}", examId, exam.getExamCode());
    }

    // ==================== 重考 ====================

    /**
     * 学生自助重考
     * 只清除答题记录和违规记录，保留考试记录以显示历史成绩
     */
    @Transactional
    public void selfRetake(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        // 验证参与者身份
        Long pCount = participantMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getExamId, examId)
                        .eq(SelfStudyParticipant::getStudentId, studentId));
        if (pCount == 0) {
            throw new BusinessException("你不是该考试的参与者");
        }

        // BUG-17修复：status=1(待考试)时，学生可能已开始答题但未提交，也应允许重考
        // status=0(生成中) 时不允许，其余状态均可重考
        if (exam.getStatus() == 0) {
            throw new BusinessException("考试试题尚未生成完毕，请稍候");
        }

        Long realExamId = exam.getExamId();
        if (realExamId == null) {
            throw new BusinessException("考试数据异常，关联的正式考试不存在");
        }

        // 查找该学生在正式考试下的考试记录
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, realExamId)
                        .eq(ExamRecord::getStudentId, studentId)
                        .eq(ExamRecord::getStatus, 0)); // 只找当前进行中（未提交）的

        if (record != null) {
            // 有未提交的记录，清除答题和违规记录
            answerRecordMapper.delete(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getRecordId, record.getId()));
            violationRecordMapper.delete(
                    new LambdaQueryWrapper<ViolationRecord>()
                            .eq(ViolationRecord::getExamId, realExamId)
                            .eq(ViolationRecord::getStudentId, studentId));
            // 物理删除未提交的考试记录
            examRecordMapper.physicalDeleteById(record.getId());
        } else {
            // 没有进行中的记录，说明已经提交过了
            // 清除旧的答题和违规记录（为重考做准备）
            // 先找到已提交的记录
            ExamRecord submittedRecord = examRecordMapper.selectOne(
                    new LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, realExamId)
                            .eq(ExamRecord::getStudentId, studentId)
                            .ne(ExamRecord::getStatus, 0)
                            .orderByDesc(ExamRecord::getId)
                            .last("LIMIT 1"));
            if (submittedRecord != null) {
                // 清除答题记录（保留考试记录用于成绩历史）
                answerRecordMapper.delete(
                        new LambdaQueryWrapper<AnswerRecord>()
                                .eq(AnswerRecord::getRecordId, submittedRecord.getId()));
                violationRecordMapper.delete(
                        new LambdaQueryWrapper<ViolationRecord>()
                                .eq(ViolationRecord::getExamId, realExamId)
                                .eq(ViolationRecord::getStudentId, studentId));
            }
        }

        // 如果考试已结束，重新激活
        if (exam.getStatus() == 3) {
            exam.setStatus(2); // 重新变为进行中
            selfStudyExamMapper.updateById(exam);
        }

        log.info("学生自助重考: examId={}, studentId={}", examId, studentId);
    }

    /**
     * 获取学生在自助考试中的历史成绩列表
     * 返回该学生在关联正式考试下的所有已提交考试记录
     */
    public List<Map<String, Object>> getExamScores(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        Long realExamId = exam.getExamId();
        if (realExamId == null) return new ArrayList<>();

        // 查询该学生在正式考试下的所有已提交记录
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, realExamId)
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0) // 已提交或自动提交
                        .orderByDesc(ExamRecord::getSubmitTime));

        List<Map<String, Object>> result = new ArrayList<>();
        int attemptNo = 1;
        for (ExamRecord r : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attemptNo", attemptNo++);
            item.put("recordId", r.getId());
            item.put("score", r.getScore());
            item.put("totalScore", exam.getTotalScore());
            item.put("passScore", exam.getPassScore());
            item.put("isPassed", r.getScore() != null && r.getScore() >= exam.getPassScore());
            item.put("submitTime", r.getSubmitTime());
            item.put("statusText", r.getStatus() == 1 ? "手动提交" : "自动提交");
            result.add(item);
        }
        return result;
    }

    /**
     * 获取自助考试关联的试卷内容（题目列表，不含答案）
     */
    public Map<String, Object> getExamPaper(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        // 验证参与者
        Long pCount = participantMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getExamId, examId)
                        .eq(SelfStudyParticipant::getStudentId, studentId));
        if (pCount == 0) {
            throw new BusinessException("你不是该考试的参与者");
        }

        if (exam.getPaperId() == null) {
            throw new BusinessException("试卷信息不存在");
        }

        // includeAnswer=false：考前预览不暴露正确答案
        Map<String, Object> result = buildPaperData(exam, null, false);
        return result;
    }

    /**
     * 获取学生在自助考试中某次答卷的试卷内容（含学生答案、正确答案、是否正确）
     * @param recordId 考试记录ID（指定查看某次答卷）
     */
    public Map<String, Object> getExamPaperWithAnswers(Long examId, Long studentId, Long recordId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        // 验证参与者
        Long pCount = participantMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyParticipant>()
                        .eq(SelfStudyParticipant::getExamId, examId)
                        .eq(SelfStudyParticipant::getStudentId, studentId));
        if (pCount == 0) {
            throw new BusinessException("你不是该考试的参与者");
        }

        // 验证recordId属于该学生
        ExamRecord record = examRecordMapper.selectById(recordId);
        if (record == null || !record.getStudentId().equals(studentId)) {
            throw new BusinessException("答卷记录不存在");
        }

        if (exam.getPaperId() == null) {
            throw new BusinessException("试卷信息不存在");
        }

        // 含答案：考后查看，显示正确答案
        Map<String, Object> result = buildPaperData(exam, recordId, true);
        // 附加答卷元数据
        result.put("recordId", recordId);
        result.put("score", record.getScore());
        result.put("submitTime", record.getSubmitTime());
        return result;
    }

    /**
     * 构建试卷数据
     * @param exam          自助考试记录
     * @param recordId      答卷ID（null=纯题目模式，不附加学生答案）
     * @param includeAnswer 是否包含正确答案（考前查看=false，考后查看=true）
     */
    private Map<String, Object> buildPaperData(SelfStudyExam exam, Long recordId, boolean includeAnswer) {
        // 获取试卷关联的题目
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, exam.getPaperId())
                        .orderByAsc(PaperQuestion::getSortOrder));

        if (paperQuestions.isEmpty()) {
            throw new BusinessException("试卷题目不存在");
        }

        List<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId).toList();

        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 获取该次答卷的答案映射
        Map<Long, AnswerRecord> answerMap = new HashMap<>();
        if (recordId != null) {
            List<AnswerRecord> answers = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getRecordId, recordId));
            answerMap = answers.stream()
                    .collect(Collectors.toMap(AnswerRecord::getQuestionId, a -> a));
        }

        List<Map<String, Object>> questions = new ArrayList<>();
        for (PaperQuestion pq : paperQuestions) {
            Question q = questionMap.get(pq.getQuestionId());
            if (q == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", q.getId());
            item.put("sortOrder", pq.getSortOrder());
            item.put("score", pq.getScore());
            item.put("type", q.getType());
            item.put("typeDesc", QuestionTypeEnum.fromCode(q.getType()).getDescription());
            item.put("content", q.getContent());
            item.put("optionA", q.getOptionA());
            item.put("optionB", q.getOptionB());
            item.put("optionC", q.getOptionC());
            item.put("optionD", q.getOptionD());
            item.put("difficulty", q.getDifficulty());
            // 正确答案和解析：仅考后查看（含答案模式）才下发，考前预览不暴露答案
            if (includeAnswer) {
                item.put("answer", q.getAnswer());
                item.put("analysis", q.getAnalysis());
            }

            // 附加学生答案信息
            AnswerRecord ar = answerMap.get(q.getId());
            if (ar != null) {
                item.put("studentAnswer", ar.getStudentAnswer());
                item.put("isCorrect", ar.getIsCorrect());
                item.put("obtainedScore", ar.getScore());
            }

            questions.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("title", exam.getTitle());
        result.put("questionCount", questions.size());
        result.put("totalScore", exam.getTotalScore());
        result.put("duration", exam.getDuration());
        result.put("questions", questions);
        return result;
    }

    // ==================== 重新生成 ====================

    /**
     * 重新生成试卷（修改prompt/duration后，清除旧试卷数据重新调用AI生成）
     * 限制：只有创建者、且考试状态不是生成中(0)时可以重新生成
     */
    @Transactional
    public GenerateOutcome regenerateExam(Long examId, Long studentId, String newPrompt, Integer newDuration) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (!exam.getCreatorId().equals(studentId)) {
            throw new BusinessException("只有创建者可以重新生成试卷");
        }
        if (exam.getStatus() == 0) {
            throw new BusinessException("试卷正在生成中，请稍候");
        }

        // ── 清理旧的正式Exam及关联数据 ──
        if (exam.getExamId() != null) {
            Long oldExamId = exam.getExamId();
            // 清理答题记录（物理删除，避免唯一索引冲突）
            examRecordMapper.physicalDeleteByExamId(oldExamId);
            // 清理白名单
            examWhitelistMapper.delete(new LambdaQueryWrapper<ExamWhitelist>()
                    .eq(ExamWhitelist::getExamId, oldExamId));
            // 删除正式Exam
            examMapper.deleteById(oldExamId);
            exam.setExamId(null);
        }

        // ── 清理旧试卷（paper + paper_question）──
        if (exam.getPaperId() != null) {
            Long oldPaperId = exam.getPaperId();
            paperQuestionMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                    .eq(PaperQuestion::getPaperId, oldPaperId));
            paperMapper.deleteById(oldPaperId);
            exam.setPaperId(null);
        }

        // ── 更新生成参数，重置为"生成中"状态 ──
        if (newPrompt != null && !newPrompt.isBlank()) {
            exam.setGeneratePrompt(newPrompt.trim());
            exam.setDescription(newPrompt.trim());
        }
        if (newDuration != null && newDuration >= 10 && newDuration <= 180) {
            exam.setDuration(newDuration);
        }
        exam.setStatus(0); // 重置为生成中
        exam.setQuestionCount(0);
        exam.setTotalScore(100);
        selfStudyExamMapper.updateById(exam);

        // ── 调用AI重新生成 ──
        return generateAndUpdateExam(examId, studentId);
    }

    // ==================== 获取考试码 ====================

    /**
     * 获取考试码（供前端显示/分享）
     */
    public String getExamCode(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (!exam.getCreatorId().equals(studentId)) {
            throw new BusinessException("只有创建者可以查看考试码");
        }
        return exam.getExamCode();
    }

    // ==================== 通过考试码查询 ====================

    /**
     * 通过考试码查询考试信息（用于加入前的预览）
     */
    public Map<String, Object> previewByExamCode(String examCode) {
        SelfStudyExam exam = selfStudyExamMapper.selectOne(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .eq(SelfStudyExam::getExamCode, examCode)
                        .eq(SelfStudyExam::getStatus, 1));

        if (exam == null) {
            throw new BusinessException("考试码无效或考试已开始/结束");
        }

        // 填充试卷标题
        if (exam.getPaperId() != null) {
            Paper paper = paperMapper.selectById(exam.getPaperId());
            if (paper != null) exam.setPaperTitle(paper.getTitle());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("examId", exam.getId());
        result.put("title", exam.getTitle());
        result.put("description", exam.getDescription());
        result.put("questionCount", exam.getQuestionCount());
        result.put("duration", exam.getDuration());
        result.put("totalScore", exam.getTotalScore());
        result.put("studentCount", exam.getStudentCount());

        return result;
    }

    /**
     * 复习路径各阶段的测验结果。
     *
     * <p>只统计从「复习路径」发起的考试（stage_key 非空）。每个阶段取最近一次
     * 已生成且有答题记录的那一场——重测就该以最新成绩为准，而不是历史最好成绩。
     */
    public List<Map<String, Object>> getStageProgress(Long studentId) {
        List<SelfStudyExam> exams = selfStudyExamMapper.selectList(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .eq(SelfStudyExam::getCreatorId, studentId)
                        .isNotNull(SelfStudyExam::getStageKey)
                        .ne(SelfStudyExam::getStageKey, "")
                        .orderByDesc(SelfStudyExam::getId));

        Map<String, Map<String, Object>> byStage = new LinkedHashMap<>();
        for (SelfStudyExam exam : exams) {
            String stageKey = exam.getStageKey();
            if (byStage.containsKey(stageKey)) {
                continue; // 已有更近的一场
            }
            if (exam.getExamId() == null) {
                continue; // 还没生成完，跳过
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stageKey", stageKey);
            row.put("selfStudyExamId", exam.getId());
            row.put("title", exam.getTitle());
            row.put("examId", exam.getExamId());
            row.put("questionCount", exam.getQuestionCount());
            row.put("passScore", exam.getPassScore());

            ExamRecord record = examRecordMapper.selectOne(
                    new LambdaQueryWrapper<ExamRecord>()
                            .eq(ExamRecord::getExamId, exam.getExamId())
                            .eq(ExamRecord::getStudentId, studentId)
                            .orderByDesc(ExamRecord::getId)
                            .last("LIMIT 1"));

            if (record != null) {
                row.put("recordId", record.getId());
                row.put("score", record.getScore());
                row.put("passed", record.getIsPassed() != null && record.getIsPassed() == 1);
                row.put("submitTime", record.getSubmitTime());
                row.put("recordStatus", record.getStatus());
                row.put("weakTopics", collectWeakTopics(record.getId()));
            }

            byStage.put(stageKey, row);
        }

        return new ArrayList<>(byStage.values());
    }

    /**
     * 统计某次考试里做错的题目分布在哪些知识点上。
     *
     * <p>这是「分数之外的第二层信息」：同样是 45 分，错在「不定积分」和错在「行列式」
     * 对应完全不同的补救动作。按知识点归类后，学生才知道下一步该回看哪一段。
     *
     * <p>没有标注知识点的题目单独归到「未标注」，不混进任何模块——宁可承认不知道，
     * 也不要瞎归类。
     */
    private List<Map<String, Object>> collectWeakTopics(Long recordId) {
        List<AnswerRecord> wrongAnswers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getRecordId, recordId)
                        .eq(AnswerRecord::getIsCorrect, 0));

        if (wrongAnswers.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> questionIds = wrongAnswers.stream()
                .map(AnswerRecord::getQuestionId).distinct().toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        Map<String, Integer> counter = new LinkedHashMap<>();
        for (AnswerRecord answer : wrongAnswers) {
            Question question = questionMap.get(answer.getQuestionId());
            String topic = question != null ? question.getKnowledgePoint() : null;
            if (topic == null || topic.isBlank()) {
                topic = "未标注知识点";
            }
            counter.merge(topic, 1, Integer::sum);
        }

        return counter.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("topic", e.getKey());
                    item.put("wrongCount", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取生成状态（前端轮询用）
     */
    public Map<String, Object> getGenerateStatus(Long examId, Long studentId) {
        SelfStudyExam exam = selfStudyExamMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", exam.getStatus());
        result.put("title", exam.getTitle());
        result.put("questionCount", exam.getQuestionCount());
        result.put("examId", exam.getExamId()); // 正式考试ID
        result.put("error", exam.getStatus() == 3 ? exam.getDescription() : null);

        return result;
    }

    /**
     * 将学生加入Exam白名单（辅助方法）
     */
    private void addToWhitelist(Long examId, Long studentId) {
        ExamWhitelist whitelist = new ExamWhitelist();
        whitelist.setExamId(examId);
        whitelist.setStudentId(studentId);
        try {
            examWhitelistMapper.insert(whitelist);
        } catch (Exception e) {
            log.debug("白名单已存在，忽略: examId={}, studentId={}", examId, studentId);
        }
    }
}
