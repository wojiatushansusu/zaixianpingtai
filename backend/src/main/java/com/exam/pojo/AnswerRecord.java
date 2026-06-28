package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("answer_record")
public class AnswerRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recordId;

    private Long questionId;

    private String studentAnswer;

    private Integer isCorrect;

    private Integer score;

    private Integer questionScore;

    private Integer teacherScore;

    private String teacherComment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String questionContent;

    @TableField(exist = false)
    private Integer questionType;

    @TableField(exist = false)
    private String correctAnswer;

    @TableField(exist = false)
    private String analysis;
}
