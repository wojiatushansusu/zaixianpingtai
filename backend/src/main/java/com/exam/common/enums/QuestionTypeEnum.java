package com.exam.common.enums;

import lombok.Getter;

/**
 * 题目类型枚举
 */
@Getter
public enum QuestionTypeEnum {

    SINGLE(1, "单选题"),
    MULTI(2, "多选题"),
    JUDGE(3, "判断题"),
    SHORT_ANSWER(4, "简答题");

    private final int code;
    private final String description;

    QuestionTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static QuestionTypeEnum fromCode(int code) {
        for (QuestionTypeEnum type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("未知的题目类型: " + code);
    }

    /**
     * 是否为客观题（可自动阅卷）
     */
    public boolean isObjective() {
        return this == SINGLE || this == MULTI || this == JUDGE;
    }
}
