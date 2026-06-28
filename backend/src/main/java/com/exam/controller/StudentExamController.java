package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.result.Result;
import com.exam.dto.SubmitExamDTO;
import com.exam.common.enums.QuestionTypeEnum;
import com.exam.mapper.*;
import com.exam.pojo.*;
import com.exam.service.ExamService;
import com.exam.service.FaceCompareService;
import com.exam.service.FaceVerifyService;
import com.exam.service.OnlineStatusService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生端考试控制器
 */
@Slf4j
@RestController
@RequestMapping("/student/exams")
@RequiredArgsConstructor
public class StudentExamController {

    private final ExamService examService;
    private final ExamRecordMapper examRecordMapper;
    private final ExamMapper examMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final QuestionMapper questionMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final UserMapper userMapper;
    private final FaceCompareService faceCompareService;
    private final FaceVerifyService faceVerifyService;
    private final OnlineStatusService onlineStatusService;
    private final SelfStudyExamMapper selfStudyExamMapper;
    private final ExamWhitelistMapper examWhitelistMapper;

    /** 允许的违规类型白名单 */
    private static final Set<String> ALLOWED_VIOLATION_TYPES = Set.of(
            "switch_screen", "no_face", "face_lost", "multi_face",
            "look_away", "forbidden_item", "ai_detect", "other",
            "keyboard_violation", "face_position_changed"
    );

    /**
     * 获取我的考试列表
     */
    @GetMapping
    public Result<List<Exam>> myExams(HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        List<Exam> exams = examService.getStudentExams(studentId);
        return Result.success(exams);
    }

    /**
     * 诊断接口 - 排查学生看不到考试的问题，支持自动修复
     */
    @GetMapping("/debug-info")
    public Result<Map<String, Object>> debugInfo(
            @RequestParam(required = false, defaultValue = "false") boolean repair,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> info = new HashMap<>();

        // 1. 当前用户信息
        User me = userMapper.selectById(studentId);
        info.put("currentUser", Map.of(
                "id", studentId,
                "username", me != null ? me.getUsername() : "N/A",
                "realName", me != null ? me.getRealName() : "N/A",
                "roleId", me != null ? me.getRoleId() : "N/A",
                "roleKey", me != null && me.getRoleKey() != null ? me.getRoleKey() : "N/A",
                "status", me != null ? me.getStatus() : "N/A"
        ));

        // 2. 所有活跃学生
        List<User> allStudents = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRoleId, 3).eq(User::getStatus, 1));
        info.put("allActiveStudents", allStudents.stream()
                .map(u -> Map.of("id", u.getId(), "username", u.getUsername(), "roleId", u.getRoleId(), "status", u.getStatus()))
                .collect(Collectors.toList()));

        // 3. 该学生的白名单
        List<ExamWhitelist> myWhitelist = examWhitelistMapper.selectList(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getStudentId, studentId));
        info.put("myWhitelistCount", myWhitelist.size());
        info.put("myWhitelistExamIds", myWhitelist.stream().map(ExamWhitelist::getExamId).collect(Collectors.toList()));

        // 4. 白名单总数和最近考试
        info.put("whitelistTotalCount", examWhitelistMapper.selectCount(null));
        List<Exam> recentExams = examMapper.selectList(
                new LambdaQueryWrapper<Exam>().orderByDesc(Exam::getId).last("LIMIT 5"));
        info.put("recentExams", recentExams.stream()
                .map(e -> Map.of("id", e.getId(), "title", e.getTitle(), "status", e.getStatus(), "deleted", e.getDeleted()))
                .collect(Collectors.toList()));

        // 5. 自动修复
        if (repair) {
            examService.repairStudentWhitelist(studentId);
            // 修复后重新查询
            List<ExamWhitelist> newWhitelist = examWhitelistMapper.selectList(
                    new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getStudentId, studentId));
            info.put("afterRepairWhitelistCount", newWhitelist.size());
            info.put("afterRepairExamIds", newWhitelist.stream().map(ExamWhitelist::getExamId).collect(Collectors.toList()));
            info.put("repairDone", true);
        }

        return Result.success(info);
    }

    /**
     * 获取考试详情
     */
    @GetMapping("/{examId}")
    public Result<Map<String, Object>> examDetail(@PathVariable Long examId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Map<String, Object> detail = examService.getExamDetail(examId, studentId);
        return Result.success(detail);
    }

    /**
     * 开始考试 - 获取试卷
     */
    @PostMapping("/{examId}/start")
    public Result<Map<String, Object>> startExam(
            @PathVariable Long examId,
            @RequestParam(required = false) String examToken,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");

        // 检查是否需要人脸核验凭证
        Exam exam = examMapper.selectById(examId);
        if (exam != null && exam.getRequireFaceVerify() != null && exam.getRequireFaceVerify() == 1) {
            if (examToken == null || examToken.isEmpty()) {
                return Result.error(400, "该考试需要人脸核验，请先完成人脸核验");
            }
            boolean valid = faceVerifyService.validateExamToken(studentId, examId, examToken);
            if (!valid) {
                return Result.error(400, "人脸核验凭证无效或已过期，请重新核验");
            }
            // 消耗凭证（一次性）
            faceVerifyService.consumeExamToken(studentId, examId, examToken);
        }

        Map<String, Object> examData = examService.startExam(examId, studentId);
        return Result.success(examData);
    }

    /**
     * 保存答案（实时保存）
     */
    @PostMapping("/{examId}/save-answer")
    public Result<Void> saveAnswer(
            @PathVariable Long examId,
            @RequestBody Map<String, String> params,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Long questionId = Long.parseLong(params.get("questionId"));
        String answer = params.get("answer");
        examService.saveAnswer(examId, studentId, questionId, answer);
        return Result.success();
    }

    /**
     * 提交考试
     */
    @PostMapping("/{examId}/submit")
    public Result<ExamRecord> submitExam(
            @PathVariable Long examId,
            @RequestBody SubmitExamDTO dto,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        dto.setExamId(examId);
        ExamRecord record = examService.submitExam(examId, studentId, dto);
        return Result.success(record);
    }

    /**
     * 上报切屏违规
     */
    @PostMapping("/{examId}/switch-screen")
    public Result<Map<String, Object>> switchScreen(
            @PathVariable Long examId,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        examService.recordSwitchScreen(examId, studentId);

        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId));

        Exam exam = examMapper.selectById(examId);

        Map<String, Object> info = new HashMap<>();
        info.put("switchCount", record != null ? record.getSwitchCount() : 0);
        info.put("maxSwitchCount", exam != null ? exam.getMaxSwitchCount() : 3);
        info.put("forceSubmit", record != null && exam != null
                && record.getSwitchCount() > exam.getMaxSwitchCount());
        return Result.success(info);
    }

    /**
     * 上传违规截图（人脸消失、无人脸等）
     */
    @PostMapping("/{examId}/violation")
    public Result<Map<String, Object>> uploadViolation(
            @PathVariable Long examId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String violationType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, defaultValue = "true") boolean createRecord,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");

        // 校验违规类型是否在白名单内
        if (violationType == null || !ALLOWED_VIOLATION_TYPES.contains(violationType)) {
            return Result.error(400, "无效的违规类型");
        }

        // 限制文件大小（5MB）
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "文件大小不能超过5MB");
        }

        try {
            // 截图直接存数据库（不再写文件系统）
            byte[] screenshotData = file.getBytes();

            // 仅当 createRecord=true 时才创建违规记录
            if (createRecord) {
                ViolationRecord violation = new ViolationRecord();
                violation.setExamId(examId);
                violation.setStudentId(studentId);
                violation.setViolationType(violationType);
                violation.setDescription(description);
                violation.setScreenshotData(screenshotData);
                violation.setOccurTime(LocalDateTime.now());
                violationRecordMapper.insert(violation);
            }

            // 返回结果（不再返回文件URL，改为标记已保存）
            Map<String, Object> result = new HashMap<>();
            result.put("saved", true);
            return Result.success(result);
        } catch (Exception e) {
            log.error("截图上传失败: examId={}, studentId={}, type={}", examId, studentId, violationType, e);
            return Result.error("截图上传失败");
        }
    }

    /**
     * 上报断网状态
     */
    @PostMapping("/{examId}/disconnect")
    public Result<Void> reportDisconnect(@PathVariable Long examId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        onlineStatusService.studentOffline(examId, studentId);
        return Result.success();
    }

    /**
     * 上报重连
     */
    @PostMapping("/{examId}/reconnect")
    public Result<Void> reportReconnect(@PathVariable Long examId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        onlineStatusService.studentOnline(examId, studentId, "reconnected");
        return Result.success();
    }

    /**
     * 离线答题数据同步（断网续传）
     */
    @PostMapping("/{examId}/offline-sync")
    public Result<Map<String, Object>> syncOfflineAnswers(
            @PathVariable Long examId,
            @RequestBody Map<String, Object> params,
            HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> answers = (List<Map<String, String>>) params.get("answers");

        if (answers == null || answers.isEmpty()) {
            return Result.success(Map.of("synced", 0));
        }

        int synced = 0;
        for (Map<String, String> answer : answers) {
            try {
                Long questionId = Long.parseLong(answer.get("questionId"));
                String answerContent = answer.get("answer");
                examService.saveAnswer(examId, studentId, questionId, answerContent);
                synced++;
            } catch (Exception e) {
                // 记录失败但继续处理其他答案
            }
        }

        return Result.success(Map.of("synced", synced, "total", answers.size()));
    }

    /**
     * 查看试卷详情（已交卷后可查看题目和学生答案）
     */
    @GetMapping("/{examId}/paper")
    public Result<Map<String, Object>> viewPaper(@PathVariable Long examId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            return Result.error(404, "考试不存在");
        }

        // 验证学生在白名单中（且已交卷）
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getSubmitTime)
                        .last("LIMIT 1"));
        if (record == null) {
            return Result.error(403, "无权查看该考试试卷");
        }

        if (exam.getPaperId() == null) {
            return Result.error(404, "试卷信息不存在");
        }

        // 获取试卷关联的题目
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<PaperQuestion>()
                        .eq(PaperQuestion::getPaperId, exam.getPaperId())
                        .orderByAsc(PaperQuestion::getSortOrder));

        if (paperQuestions.isEmpty()) {
            return Result.error(404, "试卷题目不存在");
        }

        List<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 获取学生答案
        Map<Long, AnswerRecord> answerMap = new HashMap<>();
        if (record != null) {
            List<AnswerRecord> answers = answerRecordMapper.selectList(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getRecordId, record.getId()));
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
            item.put("answer", q.getAnswer());
            item.put("analysis", q.getAnalysis());

            // 附带学生答案
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
        result.put("totalScore", questions.stream().mapToInt(q -> (Integer) q.get("score")).sum());
        result.put("duration", exam.getDuration());
        result.put("score", record.getScore());
        result.put("submitTime", record.getSubmitTime());
        result.put("questions", questions);
        return Result.success(result);
    }

    /**
     * 获取学生在某考试的所有历史答卷记录（用于"历史成绩"弹窗）
     * 仅返回已提交状态（status != 0）的记录，按提交时间倒序
     */
    @GetMapping("/{examId}/records")
    public Result<List<Map<String, Object>>> getMyRecords(@PathVariable Long examId,
                                                          HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");

        // 验证学生在白名单中
        Long whitelistCount = examWhitelistMapper.selectCount(
                new LambdaQueryWrapper<ExamWhitelist>()
                        .eq(ExamWhitelist::getExamId, examId)
                        .eq(ExamWhitelist::getStudentId, studentId));
        if (whitelistCount == 0) {
            return Result.error(403, "无权查看该考试记录");
        }

        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId)
                        .ne(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getSubmitTime));

        Exam exam = examMapper.selectById(examId);
        int totalScore = 0;
        if (exam != null && exam.getPaperId() != null) {
            List<PaperQuestion> pqs = paperQuestionMapper.selectList(
                    new LambdaQueryWrapper<PaperQuestion>().eq(PaperQuestion::getPaperId, exam.getPaperId()));
            totalScore = pqs.stream().mapToInt(PaperQuestion::getScore).sum();
        }
        final int finalTotalScore = totalScore > 0 ? totalScore : 100;

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            ExamRecord r = records.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("recordId", r.getId());
            item.put("attemptNo", records.size() - i); // 第几次，最新的是最大次
            item.put("score", r.getScore());
            item.put("totalScore", finalTotalScore);
            item.put("isPassed", r.getScore() != null && r.getScore() >= finalTotalScore * 0.6);
            item.put("statusText", r.getStatus() == 2 ? "自动提交" : "已提交");
            item.put("submitTime", r.getSubmitTime());
            result.add(item);
        }
        return Result.success(result);
    }

    /**
     * 学生自助重考（仅限自助考试）
     * 内联重考逻辑：删除答题记录、违规记录、在线状态、物理删除考试记录
     */
    @PostMapping("/{examId}/retake")
    public Result<Void> selfRetake(@PathVariable Long examId, HttpServletRequest request) {
        Long studentId = (Long) request.getAttribute("userId");
        // 仅自助考试允许学生自行重考
        Long count = selfStudyExamMapper.selectCount(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .eq(SelfStudyExam::getExamId, examId));
        if (count == 0) {
            return Result.error(403, "教师/管理员发布的考试不支持自行重考，请联系老师申请");
        }
        // 检查关联的正式考试是否已过期
        Exam exam = examMapper.selectById(examId);
        if (exam != null && exam.getEndTime() != null && LocalDateTime.now().isAfter(exam.getEndTime())) {
            return Result.error(403, "考试时间已过，无法重考");
        }

        // 查找该学生的考试记录
        ExamRecord record = examRecordMapper.selectOne(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, examId)
                        .eq(ExamRecord::getStudentId, studentId));

        // 删除答题记录
        if (record != null) {
            answerRecordMapper.delete(
                    new LambdaQueryWrapper<AnswerRecord>()
                            .eq(AnswerRecord::getRecordId, record.getId()));
        }

        // 删除违规记录
        violationRecordMapper.delete(
                new LambdaQueryWrapper<ViolationRecord>()
                        .eq(ViolationRecord::getExamId, examId)
                        .eq(ViolationRecord::getStudentId, studentId));

        // 物理删除已逻辑删除的旧记录（避免唯一索引冲突）
        examRecordMapper.physicalDeleteDeleted(examId, studentId);

        // 物理删除考试记录（必须物理删除，uk_exam_student 唯一索引不包含 deleted 字段）
        if (record != null) {
            examRecordMapper.physicalDeleteById(record.getId());
        }

        log.info("学生自助重考: examId={}, studentId={}, recordId={}",
                examId, studentId, record != null ? record.getId() : "N/A");
        return Result.success();
    }
}
