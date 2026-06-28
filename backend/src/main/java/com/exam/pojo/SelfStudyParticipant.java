package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自助考试参与记录
 */
@Data
@TableName("self_study_participant")
public class SelfStudyParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 自助考试ID */
    private Long examId;

    /** 学生ID */
    private Long studentId;

    /** 是否创建者: 0-否 1-是 */
    private Integer isCreator;

    /** 加入时间 */
    private LocalDateTime joinTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
