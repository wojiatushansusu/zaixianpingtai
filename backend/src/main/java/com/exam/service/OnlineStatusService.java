package com.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.exam.mapper.ExamOnlineStudentMapper;
import com.exam.mapper.ExamRecordMapper;
import com.exam.pojo.ExamOnlineStudent;
import com.exam.pojo.ExamRecord;
import com.exam.websocket.ExamMonitorWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 在线状态管理服务
 * 
 * 功能:
 * 1. 维护考生在线状态（基于WebSocket心跳）
 * 2. 定时检测超时未心跳的考生，标记为断网
 * 3. 断网重连时恢复状态
 * 4. 断网次数和时长统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineStatusService {

    private final ExamOnlineStudentMapper onlineStudentMapper;
    private final ExamRecordMapper examRecordMapper;
    private final ExamMonitorWebSocketHandler wsHandler;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.heartbeat.timeout:60}")
    private int heartbeatTimeout;

    /**
     * 考生上线
     */
    public void studentOnline(Long examId, Long studentId, String sessionId) {
        ExamOnlineStudent existing = onlineStudentMapper.selectOne(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getStudentId, studentId));

        if (existing != null) {
            // 重连场景
            existing.setSessionId(sessionId);
            existing.setOnlineStatus(1);
            existing.setLastHeartbeat(LocalDateTime.now());
            
            // 计算断网时长
            if (existing.getDisconnectTime() != null) {
                long disconnectSeconds = Duration.between(existing.getDisconnectTime(), LocalDateTime.now()).getSeconds();
                existing.setTotalDisconnectDuration(
                    (existing.getTotalDisconnectDuration() != null ? existing.getTotalDisconnectDuration() : 0) 
                    + (int) disconnectSeconds);
                existing.setReconnectTime(LocalDateTime.now());
            }
            
            onlineStudentMapper.updateById(existing);
            log.info("考生重连: examId={}, studentId={}, disconnectDuration={}",
                examId, studentId, existing.getTotalDisconnectDuration());
        } else {
            ExamOnlineStudent online = new ExamOnlineStudent();
            online.setExamId(examId);
            online.setStudentId(studentId);
            online.setSessionId(sessionId);
            online.setOnlineStatus(1);
            online.setLastHeartbeat(LocalDateTime.now());
            online.setDisconnectCount(0);
            online.setTotalDisconnectDuration(0);
            onlineStudentMapper.insert(online);
            log.info("考生上线: examId={}, studentId={}", examId, studentId);
        }

        // 缓存到Redis（快速查询）
        redisTemplate.opsForValue().set(
            "exam:online:" + examId + ":" + studentId, 
            "1", heartbeatTimeout + 30, TimeUnit.SECONDS);
    }

    /**
     * 更新心跳
     */
    public void updateHeartbeat(Long examId, Long studentId) {
        redisTemplate.opsForValue().set(
            "exam:online:" + examId + ":" + studentId,
            "1", heartbeatTimeout + 30, TimeUnit.SECONDS);

        onlineStudentMapper.update(null,
            new LambdaUpdateWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getStudentId, studentId)
                .set(ExamOnlineStudent::getLastHeartbeat, LocalDateTime.now()));
    }

    /**
     * 考生离线
     */
    public void studentOffline(Long examId, Long studentId) {
        onlineStudentMapper.update(null,
            new LambdaUpdateWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getStudentId, studentId)
                .set(ExamOnlineStudent::getOnlineStatus, 0)
                .set(ExamOnlineStudent::getDisconnectTime, LocalDateTime.now()));

        redisTemplate.delete("exam:online:" + examId + ":" + studentId);
        log.info("考生离线: examId={}, studentId={}", examId, studentId);
    }

    /**
     * 定时检查心跳超时（每30秒）
     */
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeat() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(heartbeatTimeout);

        // 查找所有心跳超时的在线考生
        List<ExamOnlineStudent> timeoutStudents = onlineStudentMapper.selectList(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getOnlineStatus, 1)
                .lt(ExamOnlineStudent::getLastHeartbeat, threshold));

        for (ExamOnlineStudent student : timeoutStudents) {
            // 标记为断网中
            student.setOnlineStatus(2);
            student.setDisconnectTime(student.getLastHeartbeat());
            
            // 增加断网次数
            int disconnectCount = student.getDisconnectCount() != null ? student.getDisconnectCount() : 0;
            student.setDisconnectCount(disconnectCount + 1);
            
            onlineStudentMapper.updateById(student);

            // 更新考试记录的断网信息
            updateExamRecordDisconnect(student.getExamId(), student.getStudentId(), student.getDisconnectTime());

            // 通知监考教师
            wsHandler.broadcastToExamRoom(student.getExamId(), "student_disconnect", Map.of(
                "examId", student.getExamId(),
                "studentId", student.getStudentId(),
                "disconnectTime", student.getLastHeartbeat().toString()
            ), "teacher");

            // 删除Redis缓存
            redisTemplate.delete("exam:online:" + student.getExamId() + ":" + student.getStudentId());

            log.warn("考生心跳超时: examId={}, studentId={}, lastHeartbeat={}",
                student.getExamId(), student.getStudentId(), student.getLastHeartbeat());
        }
    }

    /**
     * 更新考试记录的断网信息
     */
    private void updateExamRecordDisconnect(Long examId, Long studentId, LocalDateTime disconnectTime) {
        ExamRecord record = examRecordMapper.selectOne(
            new LambdaQueryWrapper<ExamRecord>()
                .eq(ExamRecord::getExamId, examId)
                .eq(ExamRecord::getStudentId, studentId)
                .eq(ExamRecord::getStatus, 0));

        if (record != null) {
            int disconnectCount = record.getDisconnectCount() != null ? record.getDisconnectCount() : 0;
            record.setDisconnectCount(disconnectCount + 1);
            record.setLastDisconnectTime(disconnectTime);
            examRecordMapper.updateById(record);
        }
    }

    /**
     * 获取考试的在线学生列表
     */
    public List<ExamOnlineStudent> getOnlineStudents(Long examId) {
        return onlineStudentMapper.selectList(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getOnlineStatus, 1));
    }

    /**
     * 获取考试的在线学生统计
     */
    public Map<String, Object> getOnlineStats(Long examId) {
        long total = onlineStudentMapper.selectCount(
            new LambdaQueryWrapper<ExamOnlineStudent>().eq(ExamOnlineStudent::getExamId, examId));
        long online = onlineStudentMapper.selectCount(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getOnlineStatus, 1));
        long disconnected = onlineStudentMapper.selectCount(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId)
                .eq(ExamOnlineStudent::getOnlineStatus, 2));

        return Map.of("total", total, "online", online, "disconnected", disconnected, 
            "offline", total - online - disconnected);
    }

    /**
     * 检查学生是否在线
     */
    public boolean isStudentOnline(Long examId, Long studentId) {
        String key = "exam:online:" + examId + ":" + studentId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 清理考试数据
     */
    public void cleanupExam(Long examId) {
        onlineStudentMapper.delete(
            new LambdaQueryWrapper<ExamOnlineStudent>()
                .eq(ExamOnlineStudent::getExamId, examId));
    }
}
