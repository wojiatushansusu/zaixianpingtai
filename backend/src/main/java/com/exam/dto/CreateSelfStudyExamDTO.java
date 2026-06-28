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
}
