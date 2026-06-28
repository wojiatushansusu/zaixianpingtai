package com.exam.dto;

import lombok.Data;

import java.util.List;

/**
 * 组卷结果DTO
 */
@Data
public class PaperPreviewDTO {

    /** 试卷ID（预览时可能还没有） */
    private Long paperId;

    /** 试卷名称 */
    private String title;

    /** 试卷描述 */
    private String description;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /** 题目数量 */
    private Integer questionCount;

    /** 选题详情 */
    private List<PaperQuestionItem> questions;

    @Data
    public static class PaperQuestionItem {
        private Long questionId;
        private String content;
        private Integer type;
        private String typeDesc;
        private String optionA;
        private String optionB;
        private String optionC;
        private String optionD;
        private String answer;
        private String analysis;
        private Integer difficulty;
        private String difficultyDesc;
        private Integer score;
        private List<String> tagNames;
    }
}
