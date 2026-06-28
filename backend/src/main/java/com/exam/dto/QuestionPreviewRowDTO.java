package com.exam.dto;

import lombok.Data;

/**
 * Excel预检 - 单行预检结果
 */
@Data
public class QuestionPreviewRowDTO {

    /** 行号（Excel中的行号，从1开始） */
    private int rowIndex;

    /** 题目类型 */
    private String typeStr;

    /** 题干 */
    private String content;

    /** 选项A */
    private String optionA;
    /** 选项B */
    private String optionB;
    /** 选项C */
    private String optionC;
    /** 选项D */
    private String optionD;

    /** 答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 分值 */
    private Integer score;

    /** 难度 */
    private String difficultyStr;

    /** 知识点标签 */
    private String tagNames;

    // ========== 校验结果 ==========

    /**
     * 状态: ok / warning / error
     */
    private String status;

    /** 校验通过/警告/错误的详情列表 */
    private java.util.List<String> messages;
}
