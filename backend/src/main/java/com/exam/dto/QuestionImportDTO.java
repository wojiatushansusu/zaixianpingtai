package com.exam.dto;

import lombok.Data;

/**
 * 题目Excel导入DTO（使用set方法手动映射）
 * 
 * 通过读取Excel表头动态匹配列名，兼容不同格式的文件：
 * - 系统模板：题目类型 / 题干 / 选项A-D / 正确答案 / 解析 / 分值 / 难度 / 知识点标签
 * - 用户文件：题目类型 / 大题题干 / 正确答案 / 选项A-D / 目录 等
 */
@Data
public class QuestionImportDTO {

    private String typeStr;
    private String content;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String answer;
    private String analysis;
    private Integer score;
    private String difficultyStr;
    private String tagNames;

    /** 行号（用于错误提示） */
    private int rowIndex;

    /**
     * 根据表头列名获取对应的字段名
     * 支持多种常见列名格式 + 包含匹配（如"题目类型"能匹配"题目类型（单选/多选/判断）"）
     */
    public static String resolveField(String header) {
        if (header == null) return null;
        String h = header.trim();
        if (h.isEmpty()) return null;

        // 1. 先做精确匹配（性能更好）
        switch (h) {
            // 题目类型
            case "题目类型":
            case "类型":
            case "题型":
                return "typeStr";
            // 题干
            case "题干":
            case "大题题干":
            case "题目":
            case "问题":
            case "试题内容":
            case "题目内容":
                return "content";
            // 选项
            case "选项A":
            case "A":
            case "选项 a":
            case "选项a":
                return "optionA";
            case "选项B":
            case "B":
            case "选项 b":
            case "选项b":
                return "optionB";
            case "选项C":
            case "C":
            case "选项 c":
            case "选项c":
                return "optionC";
            case "选项D":
            case "D":
            case "选项 d":
            case "选项d":
                return "optionD";
            // 答案
            case "正确答案":
            case "答案":
            case "标准答案":
            case "参考答案":
            case "正确选项":
                return "answer";
            // 解析
            case "解析":
            case "答案解析":
            case "解析说明":
            case "详解":
            case "答案解析说明":
                return "analysis";
            // 分值
            case "分值":
            case "分数":
            case "满分":
            case "默认分值":
            case "题分":
                return "score";
            // 难度
            case "难度":
            case "难度等级":
                return "difficultyStr";
            // 标签
            case "知识点标签":
            case "标签":
            case "目录":
            case "分类":
            case "知识点":
            case "章节":
            case "科目":
                return "tagNames";
        }

        // 2. 包含匹配（去除括号内说明性文字后匹配）
        //    例如 "题目类型（单选/多选/判断）" -> 匹配 "题目类型"
        String cleanH = h.replaceAll("[（(][^）)]*[）)]", "").trim();
        if (!cleanH.equals(h)) {
            return resolveField(cleanH);
        }

        // 3. 关键词包含匹配
        if (h.contains("题目类型") || h.contains("题型") || h.contains("题类") || h.contains("试题类型"))
            return "typeStr";
        if (h.contains("题干") || (h.contains("题目") && !h.contains("类型") && !h.contains("数")))
            return "content";
        if (h.contains("选项A") || (h.equals("A")))
            return "optionA";
        if (h.contains("选项B") || (h.equals("B")))
            return "optionB";
        if (h.contains("选项C") || (h.equals("C")))
            return "optionC";
        if (h.contains("选项D") || (h.equals("D")))
            return "optionD";
        if (h.contains("正确答案") || h.contains("标准答案") || h.contains("参考答案"))
            return "answer";
        if (h.contains("解析") || h.contains("详解"))
            return "analysis";
        if (h.contains("分值") || h.contains("分数"))
            return "score";
        if (h.contains("难度"))
            return "difficultyStr";
        if (h.contains("标签") || h.contains("知识点") || h.contains("章节") || h.contains("目录") || h.contains("分类"))
            return "tagNames";

        return null;
    }
}
