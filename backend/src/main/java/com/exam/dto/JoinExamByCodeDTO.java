package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通过考试码加入考试请求
 */
@Data
public class JoinExamByCodeDTO {

    @NotBlank(message = "请输入考试码")
    private String examCode;
}
