package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_token")
public class ExamToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long examId;

    /** 考试凭证Token */
    private String token;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否已使用(0-未使用 1-已使用) */
    private Integer used;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
