package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI自助考试创建请求
 */
@Data
public class CreateSelfStudyExamDTO {

    /** 生成要求，如"生成10道Java基础单选题" */
    @NotBlank(message = "请输入考试要求")
    @Size(max = 500, message = "要求描述不能超过500字")
    private String prompt;

    /** 考试时长（分钟） */
    private Integer duration = 60;

    /**
     * 所属复习阶段（如 stage-2）。
     * 从「复习路径」页发起时传入，用于把考试结果回填成阶段状态；普通出卷留空。
     */
    @Size(max = 20, message = "阶段标识过长")
    private String stageKey;
}
