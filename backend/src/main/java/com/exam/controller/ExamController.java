package com.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.exception.BusinessException;
import com.exam.common.result.Result;
import com.exam.dto.ExamDTO;
import com.exam.mapper.*;
import com.exam.pojo.*;
import com.exam.service.ExamService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/teacher/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamMapper examMapper;
    private final ExamWhitelistMapper examWhitelistMapper;
    private final ExamRecordMapper examRecordMapper;
    private final PaperMapper paperMapper;
    private final UserMapper userMapper;
    private final ExamService examService;
    private final SelfStudyExamMapper selfStudyExamMapper;

    /**
     * 分页查询考试列表
     */
    @GetMapping
    public Result<Page<Exam>> listExams(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status) {

        Page<Exam> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(title != null, Exam::getTitle, title);
        wrapper.eq(status != null, Exam::getStatus, status);

        // 过滤掉AI自助考试创建的Exam记录（学生自助考试不应出现在教师管理列表中）
        List<SelfStudyExam> selfStudyExams = selfStudyExamMapper.selectList(
                new LambdaQueryWrapper<SelfStudyExam>()
                        .isNotNull(SelfStudyExam::getExamId)
                        .select(SelfStudyExam::getExamId));
        if (!selfStudyExams.isEmpty()) {
            List<Long> selfExamIds = selfStudyExams.stream()
                    .map(SelfStudyExam::getExamId).collect(Collectors.toList());
            wrapper.notIn(Exam::getId, selfExamIds);
        }

        wrapper.orderByDesc(Exam::getCreateTime);

        Page<Exam> result = examMapper.selectPage(page, wrapper);
        result.getRecords().forEach(this::fillExamInfo);

        return Result.success(result);
    }

    /**
     * 获取考试详情
     */
    @GetMapping("/{id}")
    public Result<Exam> getExam(@PathVariable Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam != null) fillExamInfo(exam);
        return Result.success(exam);
    }

    /**
     * 创建考试
     */
    @PostMapping
    public Result<Void> createExam(@Valid @RequestBody ExamDTO dto, HttpServletRequest request) {
        Exam exam = new Exam();
        exam.setTitle(dto.getTitle());
        exam.setDescription(dto.getDescription());
        exam.setPaperId(dto.getPaperId());
        exam.setCreatorId((Long) request.getAttribute("userId"));
        exam.setStartTime(dto.getStartTime());
        exam.setEndTime(dto.getEndTime());
        exam.setDuration(dto.getDuration());
        exam.setMaxSwitchCount(dto.getMaxSwitchCount() != null ? dto.getMaxSwitchCount() : 3);
        exam.setRequireFaceVerify(dto.getRequireFaceVerify() != null ? dto.getRequireFaceVerify() : 0);
        exam.setEnableAiMonitor(dto.getEnableAiMonitor() != null ? dto.getEnableAiMonitor() : 1);
        exam.setNotice(dto.getNotice());
        exam.setStatus(0);

        // 从试卷获取及格分
        Paper paper = paperMapper.selectById(dto.getPaperId());
        if (paper != null) {
            exam.setPassScore(paper.getPassScore());
        } else {
            exam.setPassScore(60);
        }

        examMapper.insert(exam);

        // 保存白名单：如果未指定学生，自动将所有状态正常的学生加入
        List<Long> studentIds = dto.getStudentIds();
        if (studentIds == null || studentIds.isEmpty()) {
            List<User> allStudents = userMapper.selectList(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRoleId, 3)  // 学生角色
                            .eq(User::getStatus, 1)); // 状态正常
            studentIds = allStudents.stream().map(User::getId).collect(Collectors.toList());
        }
        for (Long studentId : studentIds) {
            ExamWhitelist whitelist = new ExamWhitelist();
            whitelist.setExamId(exam.getId());
            whitelist.setStudentId(studentId);
            examWhitelistMapper.insert(whitelist);
        }

        return Result.success();
    }

    /**
     * 更新考试
     */
    @PutMapping("/{id}")
    public Result<Void> updateExam(@PathVariable Long id, @Valid @RequestBody ExamDTO dto) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            return Result.error(5001, "考试不存在");
        }

        // 只有未开始的考试才能修改
        if (exam.getStatus() != 0) {
            return Result.error(5006, "考试已开始，不可修改");
        }

        exam.setTitle(dto.getTitle());
        exam.setDescription(dto.getDescription());
        exam.setPaperId(dto.getPaperId());
        exam.setStartTime(dto.getStartTime());
        exam.setEndTime(dto.getEndTime());
        exam.setDuration(dto.getDuration());
        exam.setMaxSwitchCount(dto.getMaxSwitchCount());
        if (dto.getRequireFaceVerify() != null) exam.setRequireFaceVerify(dto.getRequireFaceVerify());
        if (dto.getEnableAiMonitor() != null) exam.setEnableAiMonitor(dto.getEnableAiMonitor());
        exam.setNotice(dto.getNotice());

        examMapper.updateById(exam);

        // 更新白名单
        if (dto.getStudentIds() != null) {
            examWhitelistMapper.delete(
                    new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, id));
            for (Long studentId : dto.getStudentIds()) {
                ExamWhitelist whitelist = new ExamWhitelist();
                whitelist.setExamId(id);
                whitelist.setStudentId(studentId);
                examWhitelistMapper.insert(whitelist);
            }
        }

        return Result.success();
    }

    /**
     * 删除考试
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteExam(@PathVariable Long id) {
        Exam exam = examMapper.selectById(id);
        // BUG-10修复：exam 为 null 时提前返回，避免继续执行无效删除
        if (exam == null) {
            return Result.error(5001, "考试不存在");
        }
        if (exam.getStatus() != 0) {
            return Result.error(5006, "考试已开始，不可删除");
        }

        examWhitelistMapper.delete(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, id));
        examMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 删除考试记录（含所有关联数据）
     * 教师和管理员可用，会清除考试记录、答题记录、违规记录、在线状态、人脸核验记录等
     */
    @DeleteMapping("/{id}/records")
    public Result<Void> deleteExamWithRecords(@PathVariable Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            return Result.error(5001, "考试不存在");
        }

        try {
            examService.deleteExamWithRecords(id);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    /**
     * 获取考试白名单
     */
    @GetMapping("/{id}/whitelist")
    public Result<List<ExamWhitelist>> getWhitelist(@PathVariable Long id) {
        List<ExamWhitelist> list = examWhitelistMapper.selectList(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, id));

        list.forEach(w -> {
            User student = userMapper.selectById(w.getStudentId());
            if (student != null) {
                w.setStudentName(student.getRealName());
                w.setStudentUsername(student.getUsername());
            }
        });

        return Result.success(list);
    }

    /**
     * 获取可添加到白名单的学生列表
     */
    @GetMapping("/{id}/available-students")
    public Result<List<User>> getAvailableStudents(@PathVariable Long id) {
        // 获取已在白名单中的学生
        List<ExamWhitelist> existing = examWhitelistMapper.selectList(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, id));
        List<Long> existingIds = existing.stream()
                .map(ExamWhitelist::getStudentId)
                .collect(Collectors.toList());

        // 获取所有学生角色用户（roleId=3）
        List<User> allStudents = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRoleId, 3).eq(User::getStatus, 1));

        // 过滤掉已在白名单中的
        List<User> available = allStudents.stream()
                .filter(u -> !existingIds.contains(u.getId()))
                .collect(Collectors.toList());

        return Result.success(available);
    }

    /**
     * 获取考试的成绩列表
     */
    @GetMapping("/{id}/grades")
    public Result<List<ExamRecord>> getExamGrades(@PathVariable Long id) {
        List<ExamRecord> records = examRecordMapper.selectList(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, id)
                        .ne(ExamRecord::getStatus, 0)
                        .orderByDesc(ExamRecord::getScore));

        records.forEach(r -> {
            User student = userMapper.selectById(r.getStudentId());
            if (student != null) {
                r.setStudentName(student.getRealName());
                r.setStudentUsername(student.getUsername());
            }
        });

        return Result.success(records);
    }

    /**
     * 获取考试成绩统计
     */
    @GetMapping("/{id}/statistics")
    public Result<java.util.Map<String, Object>> getExamStatistics(@PathVariable Long id) {
        return Result.success(examService.getExamStatistics(id));
    }

    private void fillExamInfo(Exam exam) {
        Paper paper = paperMapper.selectById(exam.getPaperId());
        if (paper != null) exam.setPaperTitle(paper.getTitle());

        User creator = userMapper.selectById(exam.getCreatorId());
        if (creator != null) exam.setCreatorName(creator.getRealName());

        Long whitelistCount = examWhitelistMapper.selectCount(
                new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, exam.getId()));
        exam.setWhitelistCount(whitelistCount.intValue());

        Long submittedCount = examRecordMapper.selectCount(
                new LambdaQueryWrapper<ExamRecord>()
                        .eq(ExamRecord::getExamId, exam.getId())
                        .ne(ExamRecord::getStatus, 0));
        exam.setSubmittedCount(submittedCount.intValue());
    }

    /**
     * 检查考试数据完整性（新功能）
     * @param id 考试ID
     * @param repair 是否自动修复发现的问题
     */
    @GetMapping("/{id}/check-data-integrity")
    public Result<Map<String, Object>> checkDataIntegrity(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") boolean repair) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        int issueCount = 0;
        int fixedCount = 0;
        
        try {
            // 1. 检查考试基本信息
            Exam exam = examMapper.selectById(id);
            if (exam == null) {
                result.put("status", "error");
                result.put("message", "考试不存在");
                return Result.success(result);
            }
            
            result.put("examTitle", exam.getTitle());
            result.put("examId", exam.getId());
            
            // 2. 检查关联试卷是否存在
            Paper paper = paperMapper.selectById(exam.getPaperId());
            if (paper == null) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("type", "critical");
                issue.put("code", "MISSING_PAPER");
                issue.put("message", "关联的试卷不存在，考生无法正常考试");
                issue.put("field", "paperId");
                issue.put("value", exam.getPaperId());
                issues.add(issue);
                issueCount++;
                
                if (repair) {
                    // 自动修复：禁用该考试
                    exam.setStatus(2); // 设为已结束
                    examMapper.updateById(exam);
                    issue.put("repaired", true);
                    issue.put("fixAction", "考试已自动禁用");
                    fixedCount++;
                }
            }
            
            // 3. 检查考试时间合理性
            if (exam.getStartTime() != null && exam.getEndTime() != null) {
                LocalDateTime startTime = exam.getStartTime();
                LocalDateTime endTime = exam.getEndTime();
                
                if (startTime.isAfter(endTime)) {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("type", "critical");
                    issue.put("code", "INVALID_TIME_RANGE");
                    issue.put("message", "考试开始时间晚于结束时间");
                    issue.put("field", "startTime/endTime");
                    issue.put("value", startTime + " > " + endTime);
                    issues.add(issue);
                    issueCount++;
                    
                    if (repair) {
                        issue.put("repaired", false);
                        issue.put("fixAction", "需要管理员手动调整时间");
                    }
                }
            }
            
            // 4. 检查白名单学生是否存在
            List<ExamWhitelist> whitelist = examWhitelistMapper.selectList(
                    new LambdaQueryWrapper<ExamWhitelist>().eq(ExamWhitelist::getExamId, id));
            
            List<Map<String, Object>> invalidStudents = new ArrayList<>();
            for (ExamWhitelist wl : whitelist) {
                User student = userMapper.selectById(wl.getStudentId());
                if (student == null || student.getStatus() != 1) {
                    Map<String, Object> invalid = new HashMap<>();
                    invalid.put("studentId", wl.getStudentId());
                    invalid.put("reason", student == null ? "用户不存在" : "用户已禁用");
                    invalidStudents.add(invalid);
                }
            }
            
            if (!invalidStudents.isEmpty()) {
                Map<String, Object> issue = new HashMap<>();
                issue.put("type", "warning");
                issue.put("code", "INVALID_WHITELIST");
                issue.put("message", "白名单中包含无效学生：" + invalidStudents.size() + "个");
                issue.put("details", invalidStudents);
                issues.add(issue);
                issueCount++;
                
                if (repair && !invalidStudents.isEmpty()) {
                    // 自动修复：删除无效的白名单条目
                    for (Map<String, Object> invalid : invalidStudents) {
                        Long studentId = (Long) invalid.get("studentId");
                        examWhitelistMapper.delete(
                                new LambdaQueryWrapper<ExamWhitelist>()
                                        .eq(ExamWhitelist::getExamId, id)
                                        .eq(ExamWhitelist::getStudentId, studentId));
                    }
                    issue.put("repaired", true);
                    issue.put("fixAction", "已删除" + invalidStudents.size() + "个无效白名单记录");
                    fixedCount++;
                }
            }
            
            // 5. 检查试卷题目数量
            if (paper != null) {
                int questionCount = paper.getQuestionCount() == null ? 0 : paper.getQuestionCount();
                int totalScore = paper.getTotalScore();
                
                if (questionCount == 0) {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("type", "critical");
                    issue.put("code", "EMPTY_PAPER");
                    issue.put("message", "试卷包含0道题目");
                    issue.put("field", "paper.questionIds");
                    issues.add(issue);
                    issueCount++;
                    
                    if (repair) {
                        issue.put("repaired", false);
                        issue.put("fixAction", "需要管理员为试卷添加题目");
                    }
                }
            }
            
            // 汇总结果
            result.put("checkedAt", LocalDateTime.now());
            result.put("issueCount", issueCount);
            result.put("fixedCount", fixedCount);
            result.put("issues", issues);
            result.put("repairEnabled", repair);
            
            if (issueCount == 0) {
                result.put("status", "healthy");
                result.put("message", "所有数据完整性检查通过");
            } else if (issues.stream().anyMatch(i -> "critical".equals(i.get("type")))) {
                result.put("status", "unhealthy");
                result.put("severity", "high");
            } else {
                result.put("status", "warning");
                result.put("severity", "low");
            }
            
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "检查过程中发生异常：" + e.getMessage());
            log.error("检查考试数据完整性失败", e);
        }
        
        return Result.success(result);
    }
}
