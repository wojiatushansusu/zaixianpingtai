package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("paper")
public class Paper {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /** 题目数量 */
    private Integer questionCount;

    /** 创建者ID */
    private Long creatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段 - 创建者姓名 */
    @TableField(exist = false)
    private String creatorName;

    /** 非数据库字段 - 题目列表（仅试卷详情接口返回） */
    @TableField(exist = false)
    private List<Question> questions;
}
