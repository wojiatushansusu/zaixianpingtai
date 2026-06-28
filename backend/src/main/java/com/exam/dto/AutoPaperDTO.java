package com.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class AutoPaperDTO {

    @NotBlank(message = "试卷标题不能为空")
    private String title;

    private String description;

    @NotNull(message = "总分不能为空")
    private Integer totalScore;

    private Integer passScore;

    private Long seed;

    @Valid
    private List<TypeConfig> typeConfigs;

    private List<Long> tagIds;

    @Data
    public static class TypeConfig {
        @NotNull(message = "题目类型不能为空")
        private Integer type;

        @NotNull(message = "题目数量不能为空")
        private Integer count;

        @NotNull(message = "每题分值不能为空")
        private Integer score;

        private Integer easyRatio;

        private Integer mediumRatio;

        private Integer hardRatio;

        private List<Long> tagIds;
    }
}
