package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.mapper.ExamMapper;
import com.exam.mapper.UserMapper;
import com.exam.mapper.ViolationRecordMapper;
import com.exam.pojo.Exam;
import com.exam.pojo.User;
import com.exam.pojo.ViolationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 违规记录管理服务
 */
@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationRecordMapper violationRecordMapper;
    private final UserMapper userMapper;
    private final ExamMapper examMapper;

    /**
     * 分页查询违规记录（批量填充学生姓名，避免N+1查询）
     */
    public Page<ViolationRecord> listViolations(Integer pageNum, Integer pageSize,
                                                Long examId, Long studentId, String violationType) {
        Page<ViolationRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ViolationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(examId != null, ViolationRecord::getExamId, examId);
        wrapper.eq(studentId != null, ViolationRecord::getStudentId, studentId);
        wrapper.like(violationType != null, ViolationRecord::getViolationType, violationType);
        wrapper.orderByDesc(ViolationRecord::getOccurTime);

        Page<ViolationRecord> result = violationRecordMapper.selectPage(page, wrapper);
        batchFillStudentName(result.getRecords());
        return result;
    }

    /**
     * 获取某场考试的实时违规统计（批量填充学生姓名）
     */
    public Map<String, Object> getViolationStats(Long examId) {
        List<ViolationRecord> records = violationRecordMapper.selectList(
                new LambdaQueryWrapper<ViolationRecord>()
                        .eq(ViolationRecord::getExamId, examId)
                        .orderByDesc(ViolationRecord::getOccurTime));

        long totalCount = records.size();
        long switchCount = records.stream()
                .filter(r -> "switch_screen".equals(r.getViolationType())
                          || "SWITCH_SCREEN".equals(r.getViolationType())).count();
        long noFaceCount = records.stream()
                .filter(r -> "no_face".equals(r.getViolationType())
                          || "face_lost".equals(r.getViolationType())).count();
        long multiFaceCount = records.stream()
                .filter(r -> "multi_face".equals(r.getViolationType())).count();
        long lookAwayCount = records.stream()
                .filter(r -> "look_away".equals(r.getViolationType())).count();
        long forbiddenCount = records.stream()
                .filter(r -> "forbidden_item".equals(r.getViolationType())).count();
        long keyboardCount = records.stream()
                .filter(r -> "keyboard_violation".equals(r.getViolationType())
                          || "keyboard".equals(r.getViolationType())).count();
        long facePositionCount = records.stream()
                .filter(r -> "face_position_changed".equals(r.getViolationType())
                          || "face_moved".equals(r.getViolationType())).count();
        long aiDetectCount = multiFaceCount + lookAwayCount + forbiddenCount;
        // otherCount = 总数 - 已知分类的（包含新类型）
        long knownCount = switchCount + noFaceCount + aiDetectCount + keyboardCount + facePositionCount;
        long otherCount = totalCount - knownCount;

        // 最近20条违规（批量填充学生姓名）
        List<ViolationRecord> recentRecords = records.stream().limit(20).collect(Collectors.toList());
        batchFillStudentName(recentRecords);

        // 填充考试名称
        Exam exam = examMapper.selectById(examId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", totalCount);
        stats.put("switchCount", switchCount);
        stats.put("noFaceCount", noFaceCount);
        stats.put("multiFaceCount", multiFaceCount);
        stats.put("lookAwayCount", lookAwayCount);
        stats.put("forbiddenCount", forbiddenCount);
        stats.put("keyboardCount", keyboardCount);
        stats.put("facePositionCount", facePositionCount);
        stats.put("aiDetectCount", aiDetectCount);
        stats.put("otherCount", otherCount);
        stats.put("recentRecords", recentRecords);
        if (exam != null) {
            stats.put("examName", exam.getTitle());
        }
        return stats;
    }

    /**
     * 批量填充学生姓名（解决N+1查询问题）
     */
    private void batchFillStudentName(List<ViolationRecord> records) {
        if (records == null || records.isEmpty()) return;

        // 1. 收集所有不重复的studentId
        Set<Long> studentIds = records.stream()
                .map(ViolationRecord::getStudentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        if (studentIds.isEmpty()) return;

        // 2. 一次性批量查询所有学生
        Map<Long, String> nameMap = new HashMap<>();
        userMapper.selectBatchIds(studentIds).forEach(user ->
                nameMap.put(user.getId(), user.getRealName()));

        // 3. 填充姓名
        records.forEach(v -> {
            if (v.getStudentId() != null) {
                v.setStudentName(nameMap.get(v.getStudentId()));
            }
        });
    }
}
