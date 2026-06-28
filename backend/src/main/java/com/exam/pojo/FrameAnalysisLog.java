package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帧分析记录
 */
@Data
@TableName("frame_analysis_log")
public class FrameAnalysisLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    /** 检测到的人脸数量 */
    private Integer faceCount;

    /** 头部姿态(roll,pitch,yaw) */
    private String headPose;

    /** 是否东张西望(1-是 0-否) */
    private Integer isLookingAway;

    /** 是否检测到违禁物品(1-是 0-否) */
    private Integer forbiddenDetected;

    /** 检测置信度 */
    private Double confidence;

    /** 处理耗时(毫秒) */
    private Integer processingTimeMs;

    /** 帧时间 */
    private LocalDateTime frameTime;

    private LocalDateTime createTime;
}
