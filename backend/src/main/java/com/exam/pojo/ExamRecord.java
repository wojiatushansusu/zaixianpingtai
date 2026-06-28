package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("exam_record")
public class ExamRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private Integer score;

    /** 状态(0-未提交 1-已提交 2-自动提交) */
    private Integer status;

    /** 切屏次数 */
    private Integer switchCount;

    /** 断网次数 */
    private Integer disconnectCount;

    /** 累计断网时长(秒) */
    private Integer totalDisconnectDuration;

    /** 最后断网时间 */
    private LocalDateTime lastDisconnectTime;

    /** 是否及格 */
    private Integer isPassed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段 - 学生姓名 */
    @TableField(exist = false)
    private String studentName;

    /** 非数据库字段 - 考试名称 */
    @TableField(exist = false)
    private String examTitle;

    /** 非数据库字段 - 用户名 */
    @TableField(exist = false)
    private String studentUsername;
}
