package com.exam.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频证据
 */
@Data
@TableName("video_evidence")
public class VideoEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    /** 关联违规记录ID */
    private Long violationId;

    /** 证据类型(screenshot-截图 video_clip-视频片段) */
    private String evidenceType;

    /** 文件路径 */
    private String fileUrl;

    /** 文件二进制数据(MP4视频或JPEG截图) */
    @TableField("file_data")
    private byte[] fileData;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** 视频时长(秒) */
    private Integer duration;

    /** 缩略图路径 */
    private String thumbnailUrl;

    /** 缩略图二进制数据(JPEG) */
    @TableField("thumbnail_data")
    private byte[] thumbnailData;

    /** 采集时间 */
    private LocalDateTime captureTime;

    /** 证据描述 */
    private String description;

    /** AI分析结果(JSON) */
    private String analysisResult;

    private LocalDateTime createTime;

    /** 非数据库字段 - 学生姓名 */
    @TableField(exist = false)
    private String studentName;
}
