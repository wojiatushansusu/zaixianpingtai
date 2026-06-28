package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** 试卷ID */
    private Long paperId;

    /** 创建者ID(教师) */
    private Long creatorId;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 考试时长(分钟) */
    private Integer duration;

    /** 状态(0-未开始 1-进行中 2-已结束) */
    private Integer status;

    /** 最大允许切屏次数 */
    private Integer maxSwitchCount;

    /** 是否需要人脸核验(1-是 0-否) */
    private Integer requireFaceVerify;

    /** 是否启用AI监考(1-是 0-否)，默认1，AI自助考试为0 */
    private Integer enableAiMonitor;

    /** 考试须知 */
    private String notice;

    /** 及格分（来自试卷） */
    private Integer passScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段 - 试卷名称 */
    @TableField(exist = false)
    private String paperTitle;

    /** 非数据库字段 - 创建者姓名 */
    @TableField(exist = false)
    private String creatorName;

    /** 非数据库字段 - 白名单学生数量 */
    @TableField(exist = false)
    private Integer whitelistCount;

    /** 非数据库字段 - 已提交人数 */
    @TableField(exist = false)
    private Integer submittedCount;

    /** 非数据库字段 - 学生端用，标记该学生的提交状态（0-未提交 1-已提交 2-自动提交） */
    @TableField(exist = false)
    private String submitStatus;

    /** 非数据库字段 - 学生端用，该学生在本考试的总答卷次数（>1时可查历史成绩） */
    @TableField(exist = false)
    private Integer recordCount;

    /** 非数据库字段 - 学生端用，若该考试来源于AI自助考试，此为 self_study_exam.id */
    @TableField(exist = false)
    private Long selfStudyExamId;
}
