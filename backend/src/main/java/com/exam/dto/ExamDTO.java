package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ExamDTO {

    private Long id;

    @NotBlank(message = "考试名称不能为空")
    private String title;

    private String description;

    @NotNull(message = "试卷不能为空")
    private Long paperId;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "考试时长不能为空")
    private Integer duration;

    private Integer maxSwitchCount;

    /** 是否需要人脸核验(1-是 0-否) */
    private Integer requireFaceVerify;

    /** 是否启用AI监考(1-是 0-否) */
    private Integer enableAiMonitor;

    private String notice;

    /** 白名单学生ID列表 */
    private List<Long> studentIds;
}
