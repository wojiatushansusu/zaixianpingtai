package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考生在线状态
 */
@Data
@TableName("exam_online_student")
public class ExamOnlineStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    /** WebSocket会话ID */
    private String sessionId;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 在线状态(1-在线 0-离线 2-断网中) */
    private Integer onlineStatus;

    /** 断网次数 */
    private Integer disconnectCount;

    /** 累计断网时长(秒) */
    private Integer totalDisconnectDuration;

    /** 断网时间 */
    private LocalDateTime disconnectTime;

    /** 重连时间 */
    private LocalDateTime reconnectTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 非数据库字段 - 学生姓名 */
    @TableField(exist = false)
    private String studentName;

    /** 非数据库字段 - 用户名 */
    @TableField(exist = false)
    private String studentUsername;

    /** 非数据库字段 - 最新违规数量 */
    @TableField(exist = false)
    private Integer violationCount;
}
