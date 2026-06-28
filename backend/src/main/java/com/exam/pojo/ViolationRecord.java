package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("violation_record")
public class ViolationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    /** 违规类型 */
    private String violationType;

    /** 违规描述 */
    private String description;

    /** 违规截图路径（兼容旧数据，优先用screenshot_data） */
    private String screenshotUrl;

    /** 违规截图二进制数据(JPEG) */
    @TableField("screenshot_data")
    private byte[] screenshotData;

    /** 严重等级(1-低 2-中 3-高) */
    private Integer severity;

    /** 违规扣分(根据类型*严重度计算) */
    private Integer score;

    /** 是否已处理(1-是 0-否) */
    private Integer handled;

    /** 关联视频证据ID */
    private Long videoEvidenceId;

    /** 违规时间 */
    private LocalDateTime occurTime;

    /** 非数据库字段 - 学生姓名 */
    @TableField(exist = false)
    private String studentName;

    /** 非数据库字段 - 视频证据URL */
    @TableField(exist = false)
    private String videoEvidenceUrl;
}
