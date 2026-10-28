package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 题目类型(1-单选 2-多选 3-判断 4-简答) */
    private Integer type;

    /** 题干 */
    private String content;

    private String optionA;

    private String optionB;

    private String optionC;

    private String optionD;

    /** 正确答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 题干图片路径 */
    private String imageUrl;

    /** 默认分值 */
    private Integer score;

    /** 难度(1-简单 2-中等 3-困难) */
    private Integer difficulty;

    /**
     * 知识点：对应复习路径里的阶段模块名（如「不定积分」）。
     * 由 AI 出卷时标注，用于把错题归到具体模块上。
     */
    private String knowledgePoint;

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

    /** 非数据库字段 - 题目类型描述 */
    @TableField(exist = false)
    private String typeDesc;

    /** 非数据库字段 - 在试卷中的分值 */
    @TableField(exist = false)
    private Integer paperScore;

    /** 非数据库字段 - 在试卷中的排序 */
    @TableField(exist = false)
    private Integer sortOrder;

    /** 非数据库字段 - 难度描述 */
    @TableField(exist = false)
    private String difficultyDesc;

    /** 非数据库字段 - 标签ID列表 */
    @TableField(exist = false)
    private List<Long> tagIds;

    /** 非数据库字段 - 标签名称列表 */
    @TableField(exist = false)
    private List<String> tagNames;
}
