package com.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PaperDTO {

    private Long id;

    @NotBlank(message = "试卷名称不能为空")
    private String title;

    private String description;

    private Integer passScore;

    /** 题目列表 - 每项包含 questionId 和 score */
    @NotEmpty(message = "请至少选择一道题目")
    private List<PaperQuestionDTO> questions;

    @Data
    public static class PaperQuestionDTO {
        @NotNull(message = "题目ID不能为空")
        private Long questionId;
        @NotNull(message = "分值不能为空")
        private Integer score;
    }
}
