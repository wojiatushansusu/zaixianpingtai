package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class QuestionDTO {

    private Long id;

    @NotNull(message = "题目类型不能为空")
    private Integer type;

    @NotBlank(message = "题干不能为空")
    private String content;

    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;

    @NotBlank(message = "答案不能为空")
    private String answer;

    private String analysis;

    private Integer score;

    /** 难度(1-简单 2-中等 3-困难) */
    private Integer difficulty;

    /** 标签ID列表 */
    private List<Long> tagIds;
}
