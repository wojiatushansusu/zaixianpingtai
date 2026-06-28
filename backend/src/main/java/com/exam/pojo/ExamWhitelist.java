package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("exam_whitelist")
public class ExamWhitelist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    /** 非数据库字段 - 学生姓名 */
    @TableField(exist = false)
    private String studentName;

    /** 非数据库字段 - 学生用户名 */
    @TableField(exist = false)
    private String studentUsername;
}
