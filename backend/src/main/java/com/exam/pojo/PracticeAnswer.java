package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 练习答题记录
 */
@Data
@TableName("practice_answer")
public class PracticeAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 练习记录ID */
    private Long practiceId;

    /** 题目ID */
    private Long questionId;

    /** 学生答案 */
    private String studentAnswer;

    /** 是否正确(1-是 0-否) */
    private Integer isCorrect;

    /** 得分 */
    private Integer score;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 非数据库字段 - 题目内容 */
    @TableField(exist = false)
    private Question question;
}
