package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错题练习记录
 */
@Data
@TableName("practice_record")
public class PracticeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 练习标题 */
    private String title;

    /** 总题数 */
    private Integer totalCount;

    /** 正确题数 */
    private Integer correctCount;

    /** 得分 */
    private Integer score;

    /** 总分 */
    private Integer totalScore;

    /** 状态(0-进行中 1-已完成) */
    private Integer status;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 提交时间 */
    private LocalDateTime submitTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
