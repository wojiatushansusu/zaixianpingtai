package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自助考试表（学生创建的AI生成考试）
 */
@Data
@TableName("self_study_exam")
public class SelfStudyExam {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 考试标题 */
    private String title;

    /** 考试描述/要求 */
    private String description;

    /** 创建者（学生）ID */
    private Long creatorId;

    /** 考试码（6位），其他学生通过此码加入 */
    private String examCode;

    /** 关联试卷ID */
    private Long paperId;

    /** 考试时长（分钟） */
    private Integer duration;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /** 状态: 0-生成中 1-待考试 2-进行中 3-已结束 */
    private Integer status;

    /** 题目数量 */
    private Integer questionCount;

    /** 参与学生数（含创建者） */
    private Integer studentCount;

    /** 用户输入的AI生成要求 */
    private String generatePrompt;

    /** 关联的正式考试ID（复用Exam表） */
    private Long examId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段 - 创建者姓名 */
    @TableField(exist = false)
    private String creatorName;

    /** 非数据库字段 - 试卷标题 */
    @TableField(exist = false)
    private String paperTitle;

    /** 非数据库字段 - 当前学生的提交状态 0-未考 1-已考 */
    @TableField(exist = false)
    private Integer mySubmitStatus;
}
